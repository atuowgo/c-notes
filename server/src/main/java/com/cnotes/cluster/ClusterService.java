package com.cnotes.cluster;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.ArticleCardDto;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.cluster.dto.ClusterCardDto;
import com.cnotes.cluster.dto.ClusterDetailDto;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识网:把标签(受控标签集是 V3 的种子)升级为"主题簇",每簇维护一篇演进式综述。
 * 簇成员 = 该标签下状态为 done 的文章。
 */
@Service
@RequiredArgsConstructor
public class ClusterService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleMapper articleMapper;
    private final ClusterSummarizer summarizer;
    private final ObjectMapper om;

    /** 少于此成员数不生成综述(单篇不构成"簇")。 */
    @Value("${cluster.min-members:2}")
    private int minMembers;

    public List<ClusterCardDto> listClusters() {
        Map<String, Integer> counts = doneCountByTag();
        return tagMapper.selectList(null).stream()
            .map(t -> {
                int n = counts.getOrDefault(t.getId(), 0);
                ClusterCardDto c = new ClusterCardDto();
                c.setId(t.getId()); c.setName(t.getName()); c.setDescription(t.getDescription());
                c.setArticleCount(n);
                c.setHasSummary(t.getLivingSummary() != null && !t.getLivingSummary().isBlank());
                c.setSummaryUpdatedAt(t.getSummaryUpdatedAt());
                return c;
            })
            .filter(c -> c.getArticleCount() > 0)
            .sorted((a, b) -> Integer.compare(b.getArticleCount(), a.getArticleCount()))
            .toList();
    }

    public ClusterDetailDto detail(String tagId) {
        Tag t = tagMapper.selectById(tagId);
        if (t == null) return null;
        List<Article> members = memberArticles(tagId);
        ClusterDetailDto d = new ClusterDetailDto();
        d.setId(t.getId()); d.setName(t.getName()); d.setDescription(t.getDescription());
        d.setLivingSummary(t.getLivingSummary()); d.setSummaryUpdatedAt(t.getSummaryUpdatedAt());
        d.setArticleCount(members.size());
        d.setArticles(members.stream().map(this::toCard).toList());
        return d;
    }

    /** 重写某簇的演进式综述(成员 < minMembers 时跳过,记录成员数避免反复尝试)。 */
    @Transactional
    public void regenerate(String tagId) {
        Tag t = tagMapper.selectById(tagId);
        if (t == null) return;
        List<Article> members = memberArticles(tagId);
        Tag upd = new Tag();
        upd.setId(tagId);
        upd.setSummaryMemberCount(members.size());
        upd.setSummaryUpdatedAt(LocalDateTime.now());
        if (members.size() >= minMembers) {
            List<ClusterSummarizer.ArticleBrief> briefs = members.stream()
                .map(a -> new ClusterSummarizer.ArticleBrief(a.getTitle(), a.getSummary(), parsePoints(a.getKeyPoints())))
                .toList();
            upd.setLivingSummary(summarizer.summarize(t.getName(), briefs));
        }
        tagMapper.updateById(upd);
    }

    /** 找出需要(重)写综述的簇:成员数 >= 下限,且综述缺失或成员数已变化。 */
    public List<String> staleClusterTagIds() {
        Map<String, Integer> counts = doneCountByTag();
        return tagMapper.selectList(null).stream()
            .filter(t -> {
                int n = counts.getOrDefault(t.getId(), 0);
                if (n < minMembers) return false;
                Integer last = t.getSummaryMemberCount();
                boolean noSummary = t.getLivingSummary() == null || t.getLivingSummary().isBlank();
                return noSummary || last == null || last != n;
            })
            .map(Tag::getId)
            .toList();
    }

    private List<Article> memberArticles(String tagId) {
        List<String> articleIds = articleTagMapper.selectList(
                Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getTagId, tagId))
            .stream().map(ArticleTag::getArticleId).distinct().toList();
        if (articleIds.isEmpty()) return List.of();
        return articleMapper.selectList(Wrappers.<Article>lambdaQuery()
            .in(Article::getId, articleIds)
            .eq(Article::getStatus, "done")
            .orderByDesc(Article::getCreateTime));
    }

    /** 每个标签下 done 文章数。 */
    private Map<String, Integer> doneCountByTag() {
        Set<String> doneIds = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .select(Article::getId).eq(Article::getStatus, "done"))
            .stream().map(Article::getId).collect(Collectors.toSet());
        if (doneIds.isEmpty()) return Map.of();
        return articleTagMapper.selectList(null).stream()
            .filter(link -> doneIds.contains(link.getArticleId()))
            .collect(Collectors.groupingBy(ArticleTag::getTagId,
                Collectors.collectingAndThen(Collectors.toSet(), Set::size)));
    }

    private List<String> parsePoints(String json) {
        try { return json == null ? List.of() : om.readValue(json, new TypeReference<List<String>>(){}); }
        catch (Exception e) { return List.of(); }
    }

    private ArticleCardDto toCard(Article a) {
        ArticleCardDto c = new ArticleCardDto();
        c.setId(a.getId()); c.setTitle(a.getTitle()); c.setAuthor(a.getAuthor());
        c.setSourceType(a.getSourceType()); c.setSummary(a.getSummary());
        c.setStatus(a.getStatus()); c.setCreateTime(a.getCreateTime());
        return c;
    }
}
