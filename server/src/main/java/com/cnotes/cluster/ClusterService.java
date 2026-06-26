package com.cnotes.cluster;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.ArticleCardDto;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.cluster.dto.ClusterCardDto;
import com.cnotes.cluster.dto.ClusterDetailDto;
import com.cnotes.cluster.entity.ClusterPreference;
import com.cnotes.cluster.mapper.ClusterPreferenceMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import com.cnotes.user.CurrentUserResolver;
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
    private final com.cnotes.chat.vector.ClusterIndexer clusterIndexer;
    private final ObjectMapper om;
    private final ClusterPreferenceMapper clusterPreferenceMapper;
    private final CurrentUserResolver currentUser;

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

    /**
     * 合并簇:把 source 标签下所有文章 retag 到 target 标签,再删 source 标签/簇。
     * 综述随成员变化由后台 worker 重写,此处只动 article_tag。
     */
    @Transactional
    public ClusterDetailDto merge(String sourceId, String targetId) {
        if (sourceId == null || targetId == null) throw new IllegalArgumentException("源簇/目标簇 id 必填");
        if (sourceId.equals(targetId)) throw new IllegalArgumentException("源簇与目标簇不能相同");
        if (tagMapper.selectById(sourceId) == null) throw new IllegalArgumentException("源簇不存在");
        if (tagMapper.selectById(targetId) == null) throw new IllegalArgumentException("目标簇不存在");
        List<ArticleTag> links = articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getTagId, sourceId));
        for (ArticleTag l : links) retag(l.getArticleId(), sourceId, targetId);
        tagMapper.deleteById(sourceId);
        recordPreference("merge", sourceId, targetId);
        return detail(targetId);
    }

    /**
     * 拆分簇:建新标签,把指定文章从源簇 retag 到新簇。新簇名冲突 → IllegalArgumentException(400)。
     */
    @Transactional
    public ClusterDetailDto split(String clusterId, List<String> articleIds, String newTagName) {
        if (newTagName == null || newTagName.isBlank()) throw new IllegalArgumentException("新簇名必填");
        if (articleIds == null || articleIds.isEmpty()) throw new IllegalArgumentException("待拆出文章必填");
        if (tagMapper.selectById(clusterId) == null) throw new IllegalArgumentException("源簇不存在");
        Long dup = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, newTagName));
        if (dup != null && dup > 0) throw new IllegalArgumentException("簇名已存在:" + newTagName);
        Tag nt = new Tag();
        nt.setName(newTagName);
        tagMapper.insert(nt);
        for (String aid : articleIds) retag(aid, clusterId, nt.getId());
        recordPreference("split", clusterId, nt.getId());
        return detail(nt.getId());
    }

    /**
     * 单篇跨簇移动:把 articleId 从源簇 retag 到目标簇。源/目标簇不存在 → IllegalArgumentException。
     */
    @Transactional
    public ClusterDetailDto move(String clusterId, String articleId, String targetTagId) {
        if (articleId == null || targetTagId == null) throw new IllegalArgumentException("articleId/targetTagId 必填");
        if (tagMapper.selectById(clusterId) == null) throw new IllegalArgumentException("源簇不存在");
        if (tagMapper.selectById(targetTagId) == null) throw new IllegalArgumentException("目标簇不存在");
        if (clusterId.equals(targetTagId)) throw new IllegalArgumentException("源簇与目标簇不能相同");
        retag(articleId, clusterId, targetTagId);
        recordPreference("move", clusterId, targetTagId);
        return detail(clusterId);
    }

    /**
     * retag 一篇文章从 sourceTag 到 targetTag,规避 article_tag 的 uk_article_tag(article_id, tag_id) 唯一键:
     * 已在目标簇 → 仅删源链接(不重复插入);否则把源链接的 tag_id 改挂目标簇(保留 confidence/create_time)。
     */
    private void retag(String articleId, String sourceTagId, String targetTagId) {
        boolean hasTarget = !articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery()
                .eq(ArticleTag::getArticleId, articleId)
                .eq(ArticleTag::getTagId, targetTagId)).isEmpty();
        if (hasTarget) {
            articleTagMapper.delete(Wrappers.<ArticleTag>lambdaQuery()
                .eq(ArticleTag::getArticleId, articleId)
                .eq(ArticleTag::getTagId, sourceTagId));
        } else {
            articleTagMapper.update(null, Wrappers.<ArticleTag>lambdaUpdate()
                .set(ArticleTag::getTagId, targetTagId)
                .eq(ArticleTag::getArticleId, articleId)
                .eq(ArticleTag::getTagId, sourceTagId));
        }
    }

    /** 记一条纠偏审计(owner_id 取当前用户;测试 permitAll 下为 null)。 */
    private void recordPreference(String action, String sourceId, String targetId) {
        ClusterPreference p = new ClusterPreference();
        p.setOwnerId(currentUser.currentUserId());
        p.setAction(action);
        p.setSourceId(sourceId);
        p.setTargetId(targetId);
        clusterPreferenceMapper.insert(p);
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
