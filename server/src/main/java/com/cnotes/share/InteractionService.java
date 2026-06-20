package com.cnotes.share;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.ArticleQueryService;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.auth.ShareLevel;
import com.cnotes.auth.UserContext;
import com.cnotes.auth.entity.User;
import com.cnotes.auth.mapper.UserMapper;
import com.cnotes.share.dto.CollectedCardDto;
import com.cnotes.share.dto.PublicArticleDto;
import com.cnotes.share.entity.Bookmark;
import com.cnotes.share.entity.Collection;
import com.cnotes.share.mapper.BookmarkMapper;
import com.cnotes.share.mapper.CollectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分享/收藏/收录的写入与公开读取编排。
 * 写操作要求真实登录(无 token 的系统用户回退不算登录);可见性与能力门槛走 {@link ShareService} 生效级别。
 */
@Service
@RequiredArgsConstructor
public class InteractionService {

    private final ArticleMapper articleMapper;
    private final BookmarkMapper bookmarkMapper;
    private final CollectionMapper collectionMapper;
    private final UserMapper userMapper;
    private final ShareService shareService;
    private final ArticleQueryService articleQueryService;
    private final ObjectMapper om;

    /* ---------- 逐篇分享级别覆盖(仅本人) ---------- */

    @Transactional
    public boolean setArticleShareLevel(String articleId, String level) {
        String me = requireLogin();
        Article a = articleMapper.selectById(articleId);
        if (a == null || !me.equals(a.getOwnerId())) return false;   // 非本人按不存在处理
        // 传 null/空 → 清除覆盖,回到继承账号默认;否则规范化为合法枚举名。
        a.setShareLevel((level == null || level.isBlank()) ? null : ShareLevel.parse(level).name());
        articleMapper.updateById(a);
        return true;
    }

    /* ---------- 公开只读 ---------- */

    /** 匿名/任意用户的公开文章视图;不可见(私有)时返回 null(控制器转 404,不泄露存在性)。 */
    public PublicArticleDto publicView(String id) {
        Article a = articleMapper.selectById(id);
        if (a == null || !shareService.publiclyVisible(a)) return null;

        PublicArticleDto d = new PublicArticleDto();
        d.setId(a.getId());
        d.setTitle(a.getTitle());
        d.setAuthor(a.getAuthor());
        d.setSummary(a.getSummary());
        d.setContent(a.getContent());
        d.setUrl(a.getUrl());
        d.setKeyPoints(parsePoints(a.getKeyPoints()));
        d.setTags(articleQueryService.tagsByArticle(List.of(a.getId())).getOrDefault(a.getId(), List.of()));
        d.setCreateTime(a.getCreateTime());
        d.setEffectiveShareLevel(shareService.effectiveLevel(a).name());

        User owner = userMapper.selectById(a.getOwnerId());
        d.setOwnerId(a.getOwnerId());
        d.setOwnerNickname(owner == null ? null : owner.getNickname());
        d.setOwnerAvatarUrl(owner == null ? null : owner.getAvatarUrl());

        String me = UserContext.currentRaw();
        d.setMine(me != null && me.equals(a.getOwnerId()));
        if (me != null && !d.isMine()) {
            d.setBookmarked(hasBookmark(me, id));
            d.setCollected(hasCollection(me, id));
        }
        return d;
    }

    /* ---------- 收藏(bookmark) ---------- */

    @Transactional
    public void bookmark(String articleId) {
        String me = requireLogin();
        Article a = loadAllowed(articleId, ShareLevel.BOOKMARKABLE);
        if (me.equals(a.getOwnerId())) return;            // 本人无需收藏自己,幂等忽略
        if (hasBookmark(me, articleId)) return;
        Bookmark b = new Bookmark();
        b.setUserId(me);
        b.setArticleId(articleId);
        try {
            bookmarkMapper.insert(b);
        } catch (DuplicateKeyException ignore) {
            // 并发重复:uk_bookmark 兜底,幂等
        }
    }

    @Transactional
    public void unbookmark(String articleId) {
        String me = requireLogin();
        bookmarkMapper.delete(Wrappers.<Bookmark>lambdaQuery()
            .eq(Bookmark::getUserId, me).eq(Bookmark::getArticleId, articleId));
    }

    /* ---------- 收录(collection,链接引用) ---------- */

    @Transactional
    public void collect(String articleId, String personalNote) {
        String me = requireLogin();
        Article a = loadAllowed(articleId, ShareLevel.COLLECTABLE);
        if (me.equals(a.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能收录自己的文章");
        }
        Collection existing = collectionMapper.selectOne(Wrappers.<Collection>lambdaQuery()
            .eq(Collection::getUserId, me).eq(Collection::getSourceArticleId, articleId));
        if (existing != null) {
            // 已收录:更新个人笔记(幂等再收录视作编辑笔记)
            existing.setPersonalNote(personalNote);
            collectionMapper.updateById(existing);
            return;
        }
        Collection c = new Collection();
        c.setUserId(me);
        c.setSourceArticleId(articleId);
        c.setPersonalNote(personalNote);
        try {
            collectionMapper.insert(c);
        } catch (DuplicateKeyException ignore) {
            // 并发重复:uk_collection 兜底,幂等
        }
    }

    @Transactional
    public void uncollect(String articleId) {
        String me = requireLogin();
        collectionMapper.delete(Wrappers.<Collection>lambdaQuery()
            .eq(Collection::getUserId, me).eq(Collection::getSourceArticleId, articleId));
    }

    /** 我收录的卡片(渲染进收件箱);原文撤回/转私有标记 sourceWithdrawn,仅保留个人笔记。 */
    public List<CollectedCardDto> listCollections() {
        String me = UserContext.currentOrSystem();
        List<Collection> rows = collectionMapper.selectList(Wrappers.<Collection>lambdaQuery()
            .eq(Collection::getUserId, me).orderByDesc(Collection::getCreateTime));
        if (rows.isEmpty()) return List.of();

        List<String> articleIds = rows.stream().map(Collection::getSourceArticleId).distinct().toList();
        Map<String, Article> articleById = articleMapper.selectBatchIds(articleIds).stream()
            .collect(Collectors.toMap(Article::getId, a -> a));
        Set<String> ownerIds = articleById.values().stream().map(Article::getOwnerId).collect(Collectors.toSet());
        Map<String, String> nicknameByOwner = ownerIds.isEmpty() ? Map.of()
            : userMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? "" : u.getNickname()));
        Map<String, List<String>> tagsByArticle = articleQueryService.tagsByArticle(articleIds);

        return rows.stream().map(r -> {
            CollectedCardDto c = new CollectedCardDto();
            c.setId(r.getId());
            c.setPersonalNote(r.getPersonalNote());
            c.setCreateTime(r.getCreateTime());
            Article a = articleById.get(r.getSourceArticleId());
            if (a == null || !shareService.publiclyVisible(a)) {
                c.setSourceWithdrawn(true);     // 原文删除或转私有:仅留个人笔记
                return c;
            }
            c.setArticleId(a.getId());
            c.setTitle(a.getTitle());
            c.setSummary(a.getSummary());
            c.setAuthor(a.getAuthor());
            c.setSourceType(a.getSourceType());
            c.setTags(tagsByArticle.getOrDefault(a.getId(), List.of()));
            c.setCollectedFrom(nicknameByOwner.get(a.getOwnerId()));
            return c;
        }).toList();
    }

    /* ---------- 内部 ---------- */

    /** 加载文章并校验生效级别满足所需能力;不存在或不可见 → 404,可见但能力不足 → 403。 */
    private Article loadAllowed(String articleId, ShareLevel required) {
        Article a = articleMapper.selectById(articleId);
        if (a == null || !shareService.publiclyVisible(a)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!shareService.effectiveLevel(a).atLeast(required)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该文章未开放此操作");
        }
        return a;
    }

    private boolean hasBookmark(String userId, String articleId) {
        return bookmarkMapper.selectCount(Wrappers.<Bookmark>lambdaQuery()
            .eq(Bookmark::getUserId, userId).eq(Bookmark::getArticleId, articleId)) > 0;
    }

    private boolean hasCollection(String userId, String articleId) {
        return collectionMapper.selectCount(Wrappers.<Collection>lambdaQuery()
            .eq(Collection::getUserId, userId).eq(Collection::getSourceArticleId, articleId)) > 0;
    }

    private static String requireLogin() {
        String me = UserContext.currentRaw();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return me;
    }

    private List<String> parsePoints(String json) {
        try {
            return json == null ? List.of() : om.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
