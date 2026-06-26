package com.cnotes.cluster.auto;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.ArticleCardDto;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.cluster.auto.dto.AutoClusterCardDto;
import com.cnotes.cluster.auto.dto.AutoClusterDetailDto;
import com.cnotes.cluster.auto.entity.AutoCluster;
import com.cnotes.cluster.auto.entity.AutoClusterMember;
import com.cnotes.cluster.auto.mapper.AutoClusterMapper;
import com.cnotes.cluster.auto.mapper.AutoClusterMemberMapper;
import com.cnotes.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 语义簇(自动聚类):取当前 owner 的所有 done 文章,复用 {@link EmbeddingModel}
 * (即 @Primary 的 ArkEmbeddingModel,只读调用)为每篇向量化,按 cosine 相似度做
 * 凝聚层次聚类(average linkage,阈值 {@code cluster.auto-threshold} 默认 0.75),
 * 产出簇+成员;簇标题取 medoid(最居中)文章标题,综述由 {@link AutoClusterSummarizer} 织。
 *
 * <p>查询(list/detail)按当前用户过滤 owner_id;重算入口 {@link #recomputeForOwner(String)}
 * 由 {@link AutoClusterWorker} 跨所有 owner 周期调用(无 SecurityContext,故显式传 owner)。
 * 单篇不构成簇:< minMembers 的簇丢弃(与标签簇 cluster.min-members 约定一致)。
 */
@Service
@RequiredArgsConstructor
public class AutoClusterService {

    private final ArticleMapper articleMapper;
    private final AutoClusterMapper autoClusterMapper;
    private final AutoClusterMemberMapper autoClusterMemberMapper;
    private final EmbeddingModel embeddingModel;
    private final CurrentUserResolver currentUser;
    private final AutoClusterSummarizer summarizer;

    /** 凝聚聚类合并阈值:两簇 average linkage 相似度 >= 此值才合并。 */
    @Value("${cluster.auto-threshold:0.75}")
    private double threshold;

    /** 少于此成员数的簇不落库(单篇不构成"簇")。 */
    @Value("${cluster.auto-min-members:2}")
    private int minMembers;

    public List<AutoClusterCardDto> listAutoClusters() {
        String oid = currentUser.currentUserId();
        var q = Wrappers.<AutoCluster>lambdaQuery();
        if (oid != null) q.eq(AutoCluster::getOwnerId, oid); else q.isNull(AutoCluster::getOwnerId);
        q.orderByDesc(AutoCluster::getMemberCount);
        return autoClusterMapper.selectList(q).stream().map(this::toCard).toList();
    }

    public AutoClusterDetailDto detail(String id) {
        AutoCluster c = autoClusterMapper.selectById(id);
        if (c == null) return null;
        // 非所有者视为不存在(404),避免泄露他人数据
        if (!Objects.equals(c.getOwnerId(), currentUser.currentUserId())) return null;
        List<String> articleIds = autoClusterMemberMapper.selectList(
                Wrappers.<AutoClusterMember>lambdaQuery().eq(AutoClusterMember::getClusterId, id))
            .stream().map(AutoClusterMember::getArticleId).toList();
        List<Article> articles = articleIds.isEmpty() ? List.of() :
            articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .in(Article::getId, articleIds).orderByDesc(Article::getCreateTime));
        AutoClusterDetailDto d = new AutoClusterDetailDto();
        d.setId(c.getId()); d.setTitle(c.getTitle()); d.setMemberCount(c.getMemberCount());
        d.setSummary(c.getSummary()); d.setCreateTime(c.getCreateTime());
        d.setArticles(articles.stream().map(this::toCard).toList());
        return d;
    }

    /** worker 用:所有有 done 文章的 owner(含 null,即历史/无主数据)。 */
    public List<String> distinctOwnerIds() {
        return articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .select(Article::getOwnerId).eq(Article::getStatus, "done"))
            .stream().map(Article::getOwnerId).distinct().toList();
    }

    /**
     * 为指定 owner 重算语义簇:全量先删后插,保证幂等(成员变化时旧簇不残留)。
     * owner 可为 null(无主数据组)。
     */
    @Transactional
    public void recomputeForOwner(String ownerId) {
        List<Article> done = articleMapper.selectList(doneArticleQuery(ownerId));
        clearOwnerClusters(ownerId);
        if (done.size() < minMembers) return;   // 总量不足,不可能成簇

        // 1) 向量化:标题+摘要(摘要缺失回落 url,再缺失跳过该篇)。
        List<Article> usable = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();
        for (Article a : done) {
            String text = embedText(a);
            if (text.isBlank()) continue;
            usable.add(a);
            embeddings.add(embeddingModel.embed(text));
        }
        if (usable.size() < minMembers) return;

        // 2) 相似度矩阵 + 凝聚聚类。
        double[][] sim = buildSimMatrix(embeddings);
        List<List<Integer>> clusters = agglomerate(usable.size(), sim, threshold);

        // 3) 落库:仅保留 >= minMembers 的簇。
        for (List<Integer> members : clusters) {
            if (members.size() < minMembers) continue;
            int medoidIdx = medoid(members, sim);
            Article medoid = usable.get(medoidIdx);
            String title = nz(medoid.getTitle(), "语义簇");
            List<AutoClusterSummarizer.ArticleBrief> briefs = members.stream()
                .map(i -> new AutoClusterSummarizer.ArticleBrief(
                    usable.get(i).getTitle(), usable.get(i).getSummary()))
                .toList();
            String summary = safeSummarize(title, briefs);

            AutoCluster c = new AutoCluster();
            c.setOwnerId(ownerId);
            c.setTitle(title);
            c.setMemberCount(members.size());
            c.setSummary(summary);
            autoClusterMapper.insert(c);
            for (Integer i : members) {
                AutoClusterMember m = new AutoClusterMember();
                m.setClusterId(c.getId());
                m.setArticleId(usable.get(i).getId());
                autoClusterMemberMapper.insert(m);
            }
        }
    }

    // ---- 聚类算法 ---------------------------------------------------------

    /** cosine 相似度矩阵(对称,对角线为 1)。 */
    private double[][] buildSimMatrix(List<float[]> embeddings) {
        int n = embeddings.size();
        double[] norms = new double[n];
        for (int i = 0; i < n; i++) norms[i] = norm(embeddings.get(i));
        double[][] sim = new double[n][n];
        for (int i = 0; i < n; i++) {
            sim[i][i] = 1.0;
            for (int j = i + 1; j < n; j++) {
                double s = cosine(embeddings.get(i), embeddings.get(j), norms[i], norms[j]);
                sim[i][j] = s; sim[j][i] = s;
            }
        }
        return sim;
    }

    /**
     * 凝聚层次聚类(average linkage / UPGMA):每轮找平均相似度最高且 >= 阈值的簇对合并,
     * 直到无满足阈值的可合并对。返回各簇的成员下标集合。
     */
    private List<List<Integer>> agglomerate(int n, double[][] sim, double threshold) {
        List<Set<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < n; i++) clusters.add(new LinkedHashSet<>(List.of(i)));
        while (clusters.size() > 1) {
            int bi = -1, bj = -1; double best = -1;
            for (int a = 0; a < clusters.size(); a++) {
                for (int b = a + 1; b < clusters.size(); b++) {
                    double link = avgLinkage(clusters.get(a), clusters.get(b), sim);
                    if (link > best) { best = link; bi = a; bj = b; }
                }
            }
            if (bi < 0 || best < threshold) break;
            clusters.get(bi).addAll(clusters.get(bj));
            clusters.remove(bj);
        }
        return clusters.stream().map(List::copyOf).toList();
    }

    /** 两簇 average linkage:所有跨簇成员对的 cosine 均值。 */
    private double avgLinkage(Set<Integer> a, Set<Integer> b, double[][] sim) {
        double sum = 0; int cnt = 0;
        for (int i : a) for (int j : b) { sum += sim[i][j]; cnt++; }
        return cnt == 0 ? -1 : sum / cnt;
    }

    /** medoid:簇内平均相似度最高的成员(最居中),用作簇标题来源。 */
    private int medoid(List<Integer> members, double[][] sim) {
        int best = members.get(0); double bestAvg = -1;
        for (int i : members) {
            double sum = 0; int cnt = 0;
            for (int j : members) if (i != j) { sum += sim[i][j]; cnt++; }
            double avg = cnt == 0 ? 1.0 : sum / cnt;
            if (avg > bestAvg) { bestAvg = avg; best = i; }
        }
        return best;
    }

    // ---- 辅助 -------------------------------------------------------------

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Article>
    doneArticleQuery(String ownerId) {
        var q = Wrappers.<Article>lambdaQuery().eq(Article::getStatus, "done")
            .orderByDesc(Article::getCreateTime);
        if (ownerId != null) q.eq(Article::getOwnerId, ownerId); else q.isNull(Article::getOwnerId);
        return q;
    }

    private void clearOwnerClusters(String ownerId) {
        List<AutoCluster> existing = autoClusterMapper.selectList(
            Wrappers.<AutoCluster>lambdaQuery()
                .select(AutoCluster::getId)
                .apply(ownerId == null, "owner_id IS NULL")
                .eq(ownerId != null, AutoCluster::getOwnerId, ownerId));
        for (AutoCluster c : existing) {
            autoClusterMemberMapper.delete(Wrappers.<AutoClusterMember>lambdaQuery()
                .eq(AutoClusterMember::getClusterId, c.getId()));
        }
        if (ownerId != null) {
            autoClusterMapper.delete(Wrappers.<AutoCluster>lambdaQuery().eq(AutoCluster::getOwnerId, ownerId));
        } else {
            autoClusterMapper.delete(Wrappers.<AutoCluster>lambdaQuery().apply("owner_id IS NULL"));
        }
    }

    private String embedText(Article a) {
        String t = a.getTitle(); String s = a.getSummary();
        String text = (t == null ? "" : t) + " " + (s == null ? "" : s);
        if (text.isBlank()) text = a.getUrl() == null ? "" : a.getUrl();
        return text.trim();
    }

    /** 调 LLM 织综述;失败不阻断聚类落库(综述可空,与标签簇一致)。 */
    private String safeSummarize(String title, List<AutoClusterSummarizer.ArticleBrief> briefs) {
        try {
            return summarizer.summarize(title, briefs);
        } catch (Exception e) {
            return null;
        }
    }

    private static double norm(float[] v) {
        double s = 0; for (float f : v) s += f * f; return Math.sqrt(s);
    }

    private static double cosine(float[] a, float[] b, double na, double nb) {
        if (na == 0 || nb == 0) return 0;
        double dot = 0; int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) dot += a[i] * b[i];
        return dot / (na * nb);
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private AutoClusterCardDto toCard(AutoCluster c) {
        AutoClusterCardDto d = new AutoClusterCardDto();
        d.setId(c.getId()); d.setTitle(c.getTitle()); d.setMemberCount(c.getMemberCount());
        d.setSummary(c.getSummary());
        d.setHasSummary(c.getSummary() != null && !c.getSummary().isBlank());
        d.setCreateTime(c.getCreateTime());
        return d;
    }

    private ArticleCardDto toCard(Article a) {
        ArticleCardDto c = new ArticleCardDto();
        c.setId(a.getId()); c.setTitle(a.getTitle()); c.setAuthor(a.getAuthor());
        c.setSourceType(a.getSourceType()); c.setSummary(a.getSummary());
        c.setStatus(a.getStatus()); c.setCreateTime(a.getCreateTime());
        return c;
    }
}
