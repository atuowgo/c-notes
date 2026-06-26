package com.cnotes.link;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.ArticleCardDto;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.cluster.auto.AutoClusterService;
import com.cnotes.cluster.auto.dto.AutoClusterCardDto;
import com.cnotes.cluster.auto.dto.AutoClusterDetailDto;
import com.cnotes.link.dto.ArticleLinkDto;
import com.cnotes.link.entity.ArticleLink;
import com.cnotes.link.mapper.LinkMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章关联推荐:为给定文章算"相关"+"更深入"两类文章——
 * 相关:候选=共享标签的 done 文章(同 owner),Ark embedding cosine 排序,top-N;
 * 更深入:候选=同 auto_cluster 成员(只读调用 {@link AutoClusterService},B1 语义簇),
 *   cosine 排序 top-N,排除已作"相关"的目标,LLM 判定+理由(无 key 优雅降级保留)。
 * reason 由 {@link LinkReasoner}(ChatClient/DeepSeek)生成,无 key 优雅降级空串。
 *
 * <p>查询/list 按 owner_id 隔离(同 AutoClusterService);无库存关联时懒算并入库后返回,
 * 后续读命中库存(幂等)。link_type 取"相关"/"更深入"(对立/互补预留)。
 */
@Service
@RequiredArgsConstructor
public class LinkService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final LinkMapper linkMapper;
    private final EmbeddingModel embeddingModel;
    private final CurrentUserResolver currentUser;
    private final LinkReasoner reasoner;
    private final AutoClusterService autoClusterService;   // 只读:取同簇成员作"更深入"候选

    @Value("${link.top-n:4}")
    private int topN;

    /**
     * 列出某文章的关联推荐(按当前用户过滤);无库存关联时懒算并入库后返回。
     * 源文章不存在 / 非当前用户所有 → 返回空(不泄露存在性)。
     */
    @Transactional
    public List<ArticleLinkDto> listLinks(String articleId) {
        String oid = currentUser.currentUserId();
        Article source = articleMapper.selectById(articleId);
        if (source == null || !Objects.equals(source.getOwnerId(), oid)) return List.of();
        List<ArticleLink> stored = linksFor(articleId, oid);
        if (stored.isEmpty()) {
            computeAndPersist(source);
            stored = linksFor(articleId, oid);
        }
        return toDtos(stored);
    }

    /**
     * 核心:为 source 算关联并入库(先删后插保幂等)。
     * 1) 相关:与 source 共享至少一个标签的 done 文章(同 owner,排除自身),cosine top-N;
     * 2) 更深入:同 auto_cluster 成员(只读调 AutoClusterService),cosine top-N,排除已作"相关"
     *    的目标(同篇不重复),LLM 判定+理由(deeperReason 返回 null 表示 LLM 判否 → 跳过)。
     */
    @Transactional
    public void computeAndPersist(Article source) {
        clearLinks(source.getId(), source.getOwnerId());

        // 1) 相关:共享标签候选,cosine top-N。
        List<Article> relCandidates = candidateArticles(source);
        Set<String> linkedTargets = new LinkedHashSet<>();
        float[] srcVec = null;
        double srcNorm = 0;
        if (!relCandidates.isEmpty()) {
            srcVec = embeddingModel.embed(embedText(source));
            srcNorm = norm(srcVec);
            for (Scored s : topByCosine(relCandidates, srcVec, srcNorm)) {
                insertLink(source, s.article, "相关", reasoner.reason(source, s.article), s.sim);
                linkedTargets.add(s.article.getId());
            }
        }

        // 2) 更深入:同簇成员,cosine top-N,排除已相关目标;LLM 判否(deeperReason=null)跳过。
        List<Article> deeperCandidates = deeperCandidates(source);
        if (deeperCandidates.isEmpty()) return;
        List<Article> fresh = deeperCandidates.stream()
            .filter(c -> !linkedTargets.contains(c.getId()))
            .toList();
        if (fresh.isEmpty()) return;
        if (srcVec == null) { srcVec = embeddingModel.embed(embedText(source)); srcNorm = norm(srcVec); }
        for (Scored s : topByCosine(fresh, srcVec, srcNorm)) {
            String reason = reasoner.deeperReason(source, s.article);
            if (reason == null) continue;          // LLM 判定非更深入 → 跳过
            insertLink(source, s.article, "更深入", reason, s.sim);
        }
    }

    /** 按 cosine 相似度(>0)降序取 top-N;embedText 为空的目标跳过。 */
    private List<Scored> topByCosine(List<Article> candidates, float[] srcVec, double srcNorm) {
        List<Scored> scored = new ArrayList<>();
        for (Article c : candidates) {
            String text = embedText(c);
            if (text.isBlank()) continue;
            float[] v = embeddingModel.embed(text);
            double sim = cosine(srcVec, srcNorm, v, norm(v));
            if (sim > 0) scored.add(new Scored(c, sim));
        }
        scored.sort((a, b) -> Double.compare(b.sim, a.sim));
        return scored.stream().limit(topN).toList();
    }

    private void insertLink(Article source, Article target, String linkType, String reason, double score) {
        ArticleLink l = new ArticleLink();
        l.setOwnerId(source.getOwnerId());
        l.setArticleId(source.getId());
        l.setTargetArticleId(target.getId());
        l.setLinkType(linkType);
        l.setReason(reason);
        l.setScore(score);
        linkMapper.insert(l);
    }

    /**
     * 更深入候选:与 source 同属一个 auto_cluster(B1)的成员文章(同 owner,排除自身)。
     * 只读调用 {@link AutoClusterService#listAutoClusters()} + {@link AutoClusterService#detail(String)},
     * 不触达 auto 包内部 mapper;簇未就绪/异常 → 返回空(不影响"相关"分支)。
     */
    private List<Article> deeperCandidates(Article source) {
        List<AutoClusterCardDto> cards;
        try {
            cards = autoClusterService.listAutoClusters();
        } catch (Exception e) {
            return List.of();
        }
        Set<String> candidateIds = new LinkedHashSet<>();
        for (AutoClusterCardDto card : cards) {
            AutoClusterDetailDto d;
            try { d = autoClusterService.detail(card.getId()); }
            catch (Exception e) { continue; }
            if (d == null || d.getArticles() == null) continue;
            boolean sourceInCluster = d.getArticles().stream()
                .anyMatch(ac -> source.getId().equals(ac.getId()));
            if (!sourceInCluster) continue;
            for (ArticleCardDto ac : d.getArticles()) {
                if (!source.getId().equals(ac.getId())) candidateIds.add(ac.getId());
            }
        }
        if (candidateIds.isEmpty()) return List.of();
        var q = Wrappers.<Article>lambdaQuery()
            .in(Article::getId, candidateIds)
            .eq(Article::getStatus, "done");
        String oid = source.getOwnerId();
        if (oid != null) q.eq(Article::getOwnerId, oid); else q.isNull(Article::getOwnerId);
        return articleMapper.selectList(q);
    }

    /** 候选:与 source 共享至少一个标签、状态 done、同 owner、非自身的文章。 */
    private List<Article> candidateArticles(Article source) {
        List<String> tagIds = articleTagMapper.selectList(
                Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, source.getId()))
            .stream().map(ArticleTag::getTagId).distinct().toList();
        if (tagIds.isEmpty()) return List.of();
        List<String> candidateIds = articleTagMapper.selectList(
                Wrappers.<ArticleTag>lambdaQuery().in(ArticleTag::getTagId, tagIds))
            .stream().map(ArticleTag::getArticleId)
            .filter(id -> !id.equals(source.getId())).distinct().toList();
        if (candidateIds.isEmpty()) return List.of();
        var q = Wrappers.<Article>lambdaQuery()
            .in(Article::getId, candidateIds)
            .eq(Article::getStatus, "done");
        String oid = source.getOwnerId();
        if (oid != null) q.eq(Article::getOwnerId, oid); else q.isNull(Article::getOwnerId);
        return articleMapper.selectList(q);
    }

    private List<ArticleLink> linksFor(String articleId, String ownerId) {
        var q = Wrappers.<ArticleLink>lambdaQuery().eq(ArticleLink::getArticleId, articleId);
        if (ownerId != null) q.eq(ArticleLink::getOwnerId, ownerId); else q.isNull(ArticleLink::getOwnerId);
        return linkMapper.selectList(q.orderByDesc(ArticleLink::getScore));
    }

    private void clearLinks(String articleId, String ownerId) {
        var q = Wrappers.<ArticleLink>lambdaQuery().eq(ArticleLink::getArticleId, articleId);
        if (ownerId != null) q.eq(ArticleLink::getOwnerId, ownerId); else q.isNull(ArticleLink::getOwnerId);
        linkMapper.delete(q);
    }

    private List<ArticleLinkDto> toDtos(List<ArticleLink> links) {
        if (links.isEmpty()) return List.of();
        List<String> targetIds = links.stream().map(ArticleLink::getTargetArticleId).distinct().toList();
        Map<String, Article> byId = articleMapper.selectList(
                Wrappers.<Article>lambdaQuery().in(Article::getId, targetIds)).stream()
            .collect(Collectors.toMap(Article::getId, a -> a));
        return links.stream()
            .filter(l -> byId.containsKey(l.getTargetArticleId()))
            .map(l -> {
                ArticleLinkDto d = new ArticleLinkDto();
                d.setTargetArticle(toCard(byId.get(l.getTargetArticleId())));
                d.setLinkType(l.getLinkType());
                d.setReason(l.getReason());
                d.setScore(l.getScore());
                return d;
            }).toList();
    }

    private ArticleCardDto toCard(Article a) {
        ArticleCardDto c = new ArticleCardDto();
        c.setId(a.getId()); c.setTitle(a.getTitle()); c.setAuthor(a.getAuthor());
        c.setSourceType(a.getSourceType()); c.setSummary(a.getSummary());
        c.setStatus(a.getStatus()); c.setCreateTime(a.getCreateTime());
        return c;
    }

    /** 向量化文本:标题+摘要(与 AutoClusterService 一致);皆空回落 url。 */
    private String embedText(Article a) {
        String t = a.getTitle(); String s = a.getSummary();
        String text = (t == null ? "" : t) + " " + (s == null ? "" : s);
        if (text.isBlank()) text = a.getUrl() == null ? "" : a.getUrl();
        return text.trim();
    }

    private static double norm(float[] v) {
        double n = 0; for (float f : v) n += f * f; return Math.sqrt(n);
    }

    private static double cosine(float[] a, double na, float[] b, double nb) {
        if (na == 0 || nb == 0) return 0;
        double dot = 0; int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) dot += a[i] * b[i];
        return dot / (na * nb);
    }

    private record Scored(Article article, double sim) {}
}
