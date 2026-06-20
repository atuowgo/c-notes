package com.cnotes.cluster;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.ArticleCardDto;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.auth.UserContext;
import com.cnotes.cluster.dto.ClusterCardDto;
import com.cnotes.cluster.dto.ClusterDetailDto;
import com.cnotes.cluster.dto.ClusterSuggestionDto;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.entity.TagMerge;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import com.cnotes.tag.mapper.TagMergeMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private static final Logger log = LoggerFactory.getLogger(ClusterService.class);

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleMapper articleMapper;
    private final TagMergeMapper tagMergeMapper;
    private final ClusterSummarizer summarizer;
    private final ClusterSuggester suggester;
    private final com.cnotes.chat.vector.ClusterIndexer clusterIndexer;
    private final VectorClusterer vectorClusterer;
    private final org.springframework.ai.embedding.EmbeddingModel embeddingModel;
    private final ObjectMapper om;

    /** 少于此成员数不生成综述(单篇不构成"簇")。 */
    @Value("${cluster.min-members:2}")
    private int minMembers;

    /**
     * 纯向量聚簇 DBSCAN 的余弦距离半径(越小越严格)。默认 0.45 由真实 Ark 2048 维 embedding
     * 实测标定:同主题文章余弦距离约 0.4~0.5,跨主题约 0.8,取 0.45 可聚同主题、隔跨主题。
     */
    @Value("${cluster.vector.eps:0.45}")
    private double vectorEps;

    /** 纯向量聚簇 DBSCAN 的最小成员数(一个簇至少几篇)。 */
    @Value("${cluster.vector.min-pts:2}")
    private int vectorMinPts;

    public List<ClusterCardDto> listClusters() {
        Map<String, Integer> counts = doneCountByTag();
        return tagMapper.selectList(null).stream()
            .filter(t -> !Boolean.TRUE.equals(t.getArchived()))   // 合并归档的源簇不再出现在列表
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
        boolean summarized = members.size() >= minMembers;
        if (summarized) {
            List<ClusterSummarizer.ArticleBrief> briefs = members.stream()
                .map(a -> new ClusterSummarizer.ArticleBrief(a.getTitle(), a.getSummary(), parsePoints(a.getKeyPoints())))
                .toList();
            upd.setLivingSummary(summarizer.summarize(t.getName(), briefs));
        }
        tagMapper.updateById(upd);
        // 综述落库后,把该簇(重)索引进知识网向量库(源2),供深聊语义召回。
        if (summarized) {
            clusterIndexer.index(tagId);
        }
    }

    // ===== 知识网纠偏(V5)=====

    /** 把一篇文章从本簇(fromTagId=id)移到另一簇,改投为用户钉选;两簇均触发重写综述。 */
    @Transactional
    public ClusterDetailDto moveArticle(String fromTagId, String articleId, String toClusterId) {
        articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery()
            .eq(ArticleTag::getArticleId, articleId).eq(ArticleTag::getTagId, fromTagId));
        pin(articleId, toClusterId);
        resetSummary(fromTagId);
        resetSummary(toClusterId);
        return detail(toClusterId);
    }

    /** 合并:fromId 的全部成员并入 toId,登记重定向并归档 fromId;toId 触发重写综述。 */
    @Transactional
    public ClusterDetailDto merge(String fromId, String toId) {
        List<ArticleTag> fromLinks = articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getTagId, fromId));
        for (ArticleTag link : fromLinks) {
            pin(link.getArticleId(), toId);
        }
        articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getTagId, fromId));

        TagMerge m = new TagMerge();
        m.setFromTagId(fromId); m.setToTagId(toId);
        tagMergeMapper.insert(m);

        Tag archived = new Tag();
        archived.setId(fromId); archived.setArchived(true);
        tagMapper.updateById(archived);

        resetSummary(toId);
        return detail(toId);
    }

    /** 拆分:把 sourceId 下选定的文章迁到一个(新建或同名复用的)簇,改投为用户钉选。 */
    @Transactional
    public ClusterDetailDto split(String sourceId, String name, List<String> articleIds) {
        String newTagId = findOrCreateTag(name);
        for (String articleId : articleIds) {
            articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery()
                .eq(ArticleTag::getArticleId, articleId).eq(ArticleTag::getTagId, sourceId));
            pin(articleId, newTagId);
        }
        resetSummary(sourceId);
        resetSummary(newTagId);
        return detail(newTagId);
    }

    /** 采纳语义建议簇:复用拆分的"建簇 + 钉选成员"路径(无源簇删除)。 */
    @Transactional
    public ClusterDetailDto acceptSuggestion(String name, List<String> articleIds) {
        String newTagId = findOrCreateTag(name);
        for (String articleId : articleIds) {
            pin(articleId, newTagId);
        }
        resetSummary(newTagId);
        return detail(newTagId);
    }

    /** 语义建议簇:LLM 在现有(未归档)标签盲区里提议最多 3 个新主题;任何异常或样本不足返回空。 */
    public List<ClusterSuggestionDto> suggestions() {
        try {
            List<Article> done = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, "done").orderByDesc(Article::getCreateTime));
            if (done.size() < 3) return List.of();

            Map<String, Article> byId = done.stream()
                .collect(Collectors.toMap(Article::getId, a -> a, (a, b) -> a));
            List<ClusterSuggester.ArticleBrief> briefs = done.stream()
                .map(a -> new ClusterSuggester.ArticleBrief(a.getId(), a.getTitle(), a.getSummary()))
                .toList();
            List<String> existing = tagMapper.selectList(null).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getArchived()))
                .map(Tag::getName).toList();

            ClusterSuggester.Suggestions s = suggester.suggest(briefs, existing);
            if (s == null || s.groups() == null) return List.of();

            List<ClusterSuggestionDto> out = new ArrayList<>();
            for (ClusterSuggester.Suggestion g : s.groups()) {
                if (g == null || g.name() == null || g.name().isBlank()) continue;
                List<ArticleCardDto> cards = (g.articleIds() == null ? List.<String>of() : g.articleIds())
                    .stream().map(byId::get).filter(java.util.Objects::nonNull)
                    .map(this::toCard).toList();
                if (cards.isEmpty()) continue;
                ClusterSuggestionDto dto = new ClusterSuggestionDto();
                dto.setName(g.name()); dto.setReason(g.reason()); dto.setArticles(cards);
                out.add(dto);
            }
            return out;
        } catch (Exception e) {
            log.warn("语义建议簇生成失败,返回空列表", e);
            return List.of();
        }
    }

    /**
     * 纯向量自动聚簇:对 done 文章的内容 embedding 跑 DBSCAN(余弦距离),把语义相近、却<b>尚未被
     * 同一受控标签覆盖</b>的文章聚成新主题建议。不依赖 LLM —— 名称由簇内标题的高频词启发式生成。
     * 任何异常(无 embedding key/网络/样本不足)优雅降级为空列表。
     */
    public List<ClusterSuggestionDto> vectorSuggestions() {
        try {
            List<Article> done = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, "done").orderByDesc(Article::getCreateTime));
            if (done.size() < vectorMinPts) return List.of();

            // 1) 逐篇向量化(标题 + 摘要),与文章列表对齐。
            List<float[]> vectors = new ArrayList<>(done.size());
            for (Article a : done) {
                String text = (nz(a.getTitle()) + "\n" + nz(a.getSummary())).trim();
                vectors.add(embeddingModel.embed(text));
            }

            // 2) DBSCAN 聚簇。
            List<List<Integer>> groups = vectorClusterer.dbscan(vectors, vectorEps, vectorMinPts);
            if (groups.isEmpty()) return List.of();

            // 3) 已被「同一受控标签」覆盖的组跳过(那已经是个簇,不必再建议)。
            List<ClusterSuggestionDto> out = new ArrayList<>();
            for (List<Integer> g : groups) {
                List<Article> members = g.stream().map(done::get).toList();
                if (members.size() < vectorMinPts) continue;
                if (shareCommonActiveTag(members)) continue;

                String name = nameFromTitles(members);
                ClusterSuggestionDto dto = new ClusterSuggestionDto();
                dto.setName(name);
                dto.setReason("按文章内容向量聚类(DBSCAN)发现:这 " + members.size() + " 篇语义相近,"
                    + "现有标签尚未把它们归到同一主题。");
                dto.setArticles(members.stream().map(this::toCard).toList());
                out.add(dto);
            }
            return out;
        } catch (Exception e) {
            log.warn("向量聚簇建议生成失败,返回空列表", e);
            return List.of();
        }
    }

    /** 这组文章是否已共享某个未归档标签(已构成一个簇)。 */
    private boolean shareCommonActiveTag(List<Article> members) {
        Set<String> activeTagIds = tagMapper.selectList(null).stream()
            .filter(t -> !Boolean.TRUE.equals(t.getArchived()))
            .map(Tag::getId).collect(Collectors.toSet());
        Map<String, Long> tagHitCount = articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
                .in(ArticleTag::getArticleId, members.stream().map(Article::getId).toList()))
            .stream()
            .filter(at -> activeTagIds.contains(at.getTagId()))
            .collect(Collectors.groupingBy(ArticleTag::getTagId, Collectors.counting()));
        return tagHitCount.values().stream().anyMatch(c -> c >= members.size());
    }

    /**
     * 启发式命名:取簇内各标题里出现于最多标题的「词」(拉丁词 + 中文 2-gram),
     * 文档频 >= 2 即用作主题名;否则回退到首篇标题截断。
     */
    private String nameFromTitles(List<Article> members) {
        Map<String, Integer> docFreq = new java.util.HashMap<>();
        for (Article a : members) {
            Set<String> tokens = titleTokens(nz(a.getTitle()));
            for (String tk : tokens) docFreq.merge(tk, 1, Integer::sum);
        }
        String best = null;
        int bestFreq = 1;
        for (Map.Entry<String, Integer> e : docFreq.entrySet()) {
            int f = e.getValue();
            if (f > bestFreq || (f == bestFreq && best != null && e.getKey().length() > best.length())) {
                best = e.getKey();
                bestFreq = f;
            }
        }
        if (best != null && bestFreq >= 2) return best;
        String t = nz(members.get(0).getTitle());
        return t.isBlank() ? "新主题簇" : (t.length() > 12 ? t.substring(0, 12) + "…" : t);
    }

    /** 标题分词:拉丁词(>=2 字母数字)小写 + 中文相邻 2-gram,作为重叠候选。 */
    private Set<String> titleTokens(String title) {
        Set<String> tokens = new java.util.HashSet<>();
        if (title == null || title.isBlank()) return tokens;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Za-z0-9]{2,}").matcher(title);
        while (m.find()) tokens.add(m.group().toLowerCase());
        // 中文 2-gram:在每段连续 CJK 上滑窗。
        java.util.regex.Matcher cjk = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]{2,}").matcher(title);
        while (cjk.find()) {
            String run = cjk.group();
            for (int i = 0; i + 2 <= run.length(); i++) tokens.add(run.substring(i, i + 2));
        }
        return tokens;
    }

    /** 用户钉选:确保(articleId, tagId)存在,存在则升级为 user 来源,不重复插入。 */
    private void pin(String articleId, String tagId) {
        ArticleTag existing = articleTagMapper.selectOne(Wrappers.<ArticleTag>lambdaQuery()
            .eq(ArticleTag::getArticleId, articleId).eq(ArticleTag::getTagId, tagId).last("LIMIT 1"));
        if (existing != null) {
            ArticleTag upd = new ArticleTag();
            upd.setId(existing.getId()); upd.setSource("user");
            if (existing.getConfidence() == null) upd.setConfidence(BigDecimal.ONE);
            articleTagMapper.updateById(upd);
            return;
        }
        ArticleTag at = new ArticleTag();
        at.setArticleId(articleId); at.setTagId(tagId);
        at.setSource("user"); at.setConfidence(BigDecimal.ONE);
        articleTagMapper.insert(at);
    }

    /** 同名复用(私有标签池:按所有者范围内同名),否则新建标签;返回标签 id。 */
    private String findOrCreateTag(String name) {
        String owner = UserContext.currentOrSystem();
        Tag existing = tagMapper.selectOne(
            Wrappers.<Tag>lambdaQuery().eq(Tag::getOwnerId, owner).eq(Tag::getName, name).last("LIMIT 1"));
        if (existing != null) return existing.getId();
        Tag t = new Tag(); t.setName(name); t.setOwnerId(owner);   // 私有标签池:归属当前用户
        tagMapper.insert(t);
        return t.getId();
    }

    /** 清空成员数,迫使下一轮 worker 重写该簇综述。 */
    private void resetSummary(String tagId) {
        tagMapper.update(null, Wrappers.<Tag>lambdaUpdate()
            .eq(Tag::getId, tagId).set(Tag::getSummaryMemberCount, null));
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

    private static String nz(String s) { return s == null ? "" : s; }

    private ArticleCardDto toCard(Article a) {
        ArticleCardDto c = new ArticleCardDto();
        c.setId(a.getId()); c.setTitle(a.getTitle()); c.setAuthor(a.getAuthor());
        c.setSourceType(a.getSourceType()); c.setSummary(a.getSummary());
        c.setStatus(a.getStatus()); c.setCreateTime(a.getCreateTime());
        return c;
    }
}
