package com.cnotes.plaza;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.ArticleQueryService;
import com.cnotes.article.entity.Article;
import com.cnotes.auth.entity.User;
import com.cnotes.auth.mapper.UserMapper;
import com.cnotes.plaza.dto.PlazaCardDto;
import com.cnotes.plaza.dto.PlazaPage;
import com.cnotes.plaza.dto.PublicProfileDto;
import com.cnotes.plaza.mapper.PlazaMapper;
import com.cnotes.relation.entity.ArticleRelation;
import com.cnotes.relation.mapper.ArticleRelationMapper;
import com.cnotes.share.ShareService;
import com.cnotes.share.entity.Bookmark;
import com.cnotes.share.entity.Collection;
import com.cnotes.share.mapper.BookmarkMapper;
import com.cnotes.share.mapper.CollectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 精品广场:发现流(质量分排序) + 用户公开主页。
 * 质量分 = 行为分(收录/收藏/点赞/评论) + AI深度分(摘要丰富度 + 知识网连通度);
 * 发现流再乘新鲜度衰减。规模化后候选池+内存算分需迁移到带索引的检索层(见诚实边界)。
 */
@Service
@RequiredArgsConstructor
public class PlazaService {

    private final PlazaMapper plazaMapper;
    private final UserMapper userMapper;
    private final BookmarkMapper bookmarkMapper;
    private final CollectionMapper collectionMapper;
    private final ArticleRelationMapper relationMapper;
    private final ArticleQueryService articleQueryService;
    private final ShareService shareService;
    private final PlazaScoreProperties weights;

    /** 发现流:全平台公开文章。sort="recent" 按最新,否则按 [质量分 × 新鲜度]。 */
    public PlazaPage discover(String sort, int page, int size) {
        List<Article> candidates = plazaMapper.findPublicCandidates(weights.getCandidateCap());
        return paginateScored(candidates, sort, page, size);
    }

    /** 某用户的公开文章(主页「已分享文章」),按最新。 */
    public PlazaPage userArticles(String userId, int page, int size) {
        List<Article> list = plazaMapper.findPublicByOwner(userId, weights.getCandidateCap());
        return paginateScored(list, "recent", page, size);
    }

    public PublicProfileDto profile(String userId) {
        User u = userMapper.selectById(userId);
        if (u == null) return null;
        List<Article> publics = plazaMapper.findPublicByOwner(userId, weights.getCandidateCap());
        List<String> ids = publics.stream().map(Article::getId).toList();

        PublicProfileDto d = new PublicProfileDto();
        d.setUserId(u.getId());
        d.setNickname(u.getNickname());
        d.setAvatarUrl(u.getAvatarUrl());
        d.setPublicCount(publics.size());
        d.setCollectedTotal(countCollections(ids).values().stream().mapToLong(Long::longValue).sum());
        d.setBookmarkedTotal(countBookmarks(ids).values().stream().mapToLong(Long::longValue).sum());
        d.setFollowing(0);
        d.setFollowers(0);
        return d;
    }

    /* ---------- 内部:算分 + 分页 ---------- */

    private PlazaPage paginateScored(List<Article> candidates, String sort, int page, int size) {
        if (candidates.isEmpty()) return new PlazaPage(List.of(), 0);
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 50);

        List<String> ids = candidates.stream().map(Article::getId).toList();
        Map<String, Long> bookmarks = countBookmarks(ids);
        Map<String, Long> collects = countCollections(ids);
        Map<String, Long> degrees = countDegrees(ids);
        Map<String, List<String>> tags = articleQueryService.tagsByArticle(ids);
        Set<String> ownerIds = candidates.stream().map(Article::getOwnerId).collect(Collectors.toSet());
        Map<String, User> owners = ownerIds.isEmpty() ? Map.of()
            : userMapper.selectBatchIds(ownerIds).stream().collect(Collectors.toMap(User::getId, x -> x));

        boolean byRecent = "recent".equalsIgnoreCase(sort);
        record Scored(PlazaCardDto card, double ranking) {}
        List<Scored> scored = candidates.stream().map(a -> {
            long bm = bookmarks.getOrDefault(a.getId(), 0L);
            long col = collects.getOrDefault(a.getId(), 0L);
            long deg = degrees.getOrDefault(a.getId(), 0L);
            double behavior = col * weights.getCollect() + bm * weights.getBookmark();
            double aiDepth = summaryRichness(a.getSummary()) + deg * weights.getDegree();
            double quality = behavior + weights.getAiWeight() * aiDepth;
            double ranking = byRecent ? 0 : quality * freshness(a.getCreateTime());

            PlazaCardDto c = new PlazaCardDto();
            c.setId(a.getId());
            c.setTitle(a.getTitle());
            c.setAuthor(a.getAuthor());
            c.setSummary(a.getSummary());
            c.setSourceType(a.getSourceType());
            c.setCreateTime(a.getCreateTime());
            c.setTags(tags.getOrDefault(a.getId(), List.of()));
            c.setBookmarkCount(bm);
            c.setCollectCount(col);
            c.setQualityScore((int) Math.round(quality));
            c.setEffectiveShareLevel(shareService.effectiveLevel(a).name());
            User o = owners.get(a.getOwnerId());
            c.setOwnerId(a.getOwnerId());
            c.setOwnerNickname(o == null ? null : o.getNickname());
            c.setOwnerAvatarUrl(o == null ? null : o.getAvatarUrl());
            return new Scored(c, ranking);
        }).collect(Collectors.toList());

        // recent:候选已按 create_time desc;score:按 ranking 降序。
        if (!byRecent) scored.sort((x, y) -> Double.compare(y.ranking(), x.ranking()));

        long total = scored.size();
        int from = Math.min((p - 1) * s, scored.size());
        int to = Math.min(from + s, scored.size());
        List<PlazaCardDto> items = scored.subList(from, to).stream().map(Scored::card).toList();
        return new PlazaPage(items, total);
    }

    private Map<String, Long> countBookmarks(List<String> articleIds) {
        if (articleIds.isEmpty()) return Map.of();
        return bookmarkMapper.selectList(Wrappers.<Bookmark>lambdaQuery().in(Bookmark::getArticleId, articleIds))
            .stream().collect(Collectors.groupingBy(Bookmark::getArticleId, Collectors.counting()));
    }

    private Map<String, Long> countCollections(List<String> articleIds) {
        if (articleIds.isEmpty()) return Map.of();
        return collectionMapper.selectList(Wrappers.<Collection>lambdaQuery().in(Collection::getSourceArticleId, articleIds))
            .stream().collect(Collectors.groupingBy(Collection::getSourceArticleId, Collectors.counting()));
    }

    /** 连通度:article_relation 中以该文为任一端点的边数(无向度数)。 */
    private Map<String, Long> countDegrees(List<String> articleIds) {
        if (articleIds.isEmpty()) return Map.of();
        Set<String> idSet = Set.copyOf(articleIds);
        List<ArticleRelation> rels = relationMapper.selectList(Wrappers.<ArticleRelation>lambdaQuery()
            .in(ArticleRelation::getFromArticleId, articleIds)
            .or().in(ArticleRelation::getToArticleId, articleIds));
        Map<String, Long> deg = new HashMap<>();
        for (ArticleRelation r : rels) {
            if (idSet.contains(r.getFromArticleId())) deg.merge(r.getFromArticleId(), 1L, Long::sum);
            if (idSet.contains(r.getToArticleId())) deg.merge(r.getToArticleId(), 1L, Long::sum);
        }
        return deg;
    }

    private double summaryRichness(String summary) {
        if (summary == null || summary.isBlank()) return 0;
        return Math.min(summary.length() / 80.0, 5.0);   // 摘要越充实越高,封顶 5 分
    }

    private double freshness(LocalDateTime createTime) {
        if (createTime == null) return 0.5;
        double ageDays = Math.max(0, Duration.between(createTime, LocalDateTime.now()).toHours() / 24.0);
        double halflife = Math.max(0.5, weights.getFreshnessHalflifeDays());
        return Math.pow(2, -ageDays / halflife);    // 半衰期衰减
    }
}
