package com.cnotes.relation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.ArticleQueryService;
import com.cnotes.article.dto.ArticleCardDto;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.relation.dto.RelatedArticleDto;
import com.cnotes.relation.entity.ArticleRelation;
import com.cnotes.relation.mapper.ArticleRelationMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章↔文章 关联(「为什么相关」)。优先返回已落库的连边;否则基于共享标签找候选,
 * 交由 LLM 选出真正相关的若干篇并给出关系类型与理由,落库后返回;LLM 不可用时
 * 按共享标签数量兜底。related() 永不抛出。
 */
@Service
public class RelationService {

    private static final Logger log = LoggerFactory.getLogger(RelationService.class);

    /** 内联默认上限:最多关联 4 篇。 */
    private static final int MAX_RELATED = 4;
    private static final int CANDIDATE_LIMIT = 12;
    private static final Set<String> ALLOWED_TYPES = Set.of("同概念", "互补", "对立", "延伸");
    private static final String FALLBACK_TYPE = "相关";
    private static final String FALLBACK_REASON = "同主题标签近邻";

    private final ArticleRelationMapper relationMapper;
    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleQueryService articleQueryService;
    private final ChatClient chatClient;

    public RelationService(ArticleRelationMapper relationMapper,
                           ArticleMapper articleMapper,
                           ArticleTagMapper articleTagMapper,
                           ArticleQueryService articleQueryService,
                           ChatClient.Builder builder) {  // Spring AI 自动配置注入
        this.relationMapper = relationMapper;
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.articleQueryService = articleQueryService;
        this.chatClient = builder.build();
    }

    /** LLM 结构化输出:从候选中选出的一条关联。 */
    public record RelationPick(String articleId, String relationType, String reason) {}
    public record RelationResult(List<RelationPick> relations) {}

    public List<RelatedArticleDto> related(String articleId) {
        try {
            // 1) 已有连边:直接装配返回
            List<ArticleRelation> existing = relationMapper.selectList(
                Wrappers.<ArticleRelation>lambdaQuery()
                    .eq(ArticleRelation::getFromArticleId, articleId)
                    .orderByAsc(ArticleRelation::getCreateTime));
            if (!existing.isEmpty()) {
                return toDtos(existing);
            }

            // 2) 计算:仅对 done 文章
            Article from = articleMapper.selectById(articleId);
            if (from == null || !"done".equals(from.getStatus())) {
                return List.of();
            }

            List<String> tagIds = articleTagMapper.selectList(
                    Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleId, articleId))
                .stream().map(ArticleTag::getTagId).distinct().toList();
            if (tagIds.isEmpty()) return List.of();

            // 共享 >=1 标签的其它文章,按共享标签数量排序,取前 CANDIDATE_LIMIT
            List<ArticleTag> sharing = articleTagMapper.selectList(
                Wrappers.<ArticleTag>lambdaQuery()
                    .in(ArticleTag::getTagId, tagIds)
                    .ne(ArticleTag::getArticleId, articleId));
            Map<String, Long> sharedCount = sharing.stream()
                .collect(Collectors.groupingBy(ArticleTag::getArticleId, Collectors.counting()));
            if (sharedCount.isEmpty()) return List.of();

            List<String> candidateIds = sharedCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

            // 仅保留 done 的候选,保持按共享数量排序
            Map<String, Article> byId = articleMapper.selectBatchIds(candidateIds).stream()
                .filter(a -> "done".equals(a.getStatus()))
                .collect(Collectors.toMap(Article::getId, a -> a));
            List<Article> candidates = candidateIds.stream()
                .map(byId::get)
                .filter(a -> a != null)
                .limit(CANDIDATE_LIMIT)
                .toList();
            if (candidates.isEmpty()) return List.of();

            List<ArticleRelation> chosen = pickByLlm(from, candidates);
            if (chosen.isEmpty()) {
                chosen = fallback(articleId, candidates);
            }
            if (chosen.isEmpty()) return List.of();

            for (ArticleRelation r : chosen) {
                relationMapper.insert(r);
            }
            return toDtos(chosen);
        } catch (Exception e) {
            log.warn("related() failed for article {}: {}", articleId, e.toString());
            return List.of();
        }
    }

    private List<ArticleRelation> pickByLlm(Article from, List<Article> candidates) {
        try {
            String briefs = candidates.stream()
                .map(a -> "- id:" + a.getId() + " 《" + nz(a.getTitle()) + "》摘要:" + nz(a.getSummary()))
                .collect(Collectors.joining("\n"));
            RelationResult result = chatClient.prompt()
                .system("你在为一篇文章挑选「为什么相关」的近邻文章。只从候选中选出至多 "
                    + MAX_RELATED + " 篇与当前文章确有实质关联的文章。"
                    + "每条给出 relationType,取值必须是:同概念、互补、对立、延伸 之一;"
                    + "并给出一句话中文 reason(不超过 60 字)。"
                    + "articleId 必须来自候选 id。若都不相关,返回空列表。")
                .user(u -> u.text("当前文章:《{title}》\n摘要:{summary}\n\n候选文章:\n{briefs}")
                            .param("title", nz(from.getTitle()))
                            .param("summary", nz(from.getSummary()))
                            .param("briefs", briefs))
                .call()
                .entity(RelationResult.class);

            if (result == null || result.relations() == null) return List.of();

            Set<String> candidateIds = candidates.stream().map(Article::getId).collect(Collectors.toSet());
            // 去重 + 校验 id/类型,限上限
            Map<String, ArticleRelation> uniq = new LinkedHashMap<>();
            for (RelationPick p : result.relations()) {
                if (p == null || p.articleId() == null) continue;
                if (!candidateIds.contains(p.articleId())) continue;
                if (uniq.containsKey(p.articleId())) continue;
                String type = ALLOWED_TYPES.contains(p.relationType()) ? p.relationType() : FALLBACK_TYPE;
                String reason = (p.reason() == null || p.reason().isBlank()) ? FALLBACK_REASON : p.reason();
                if (reason.length() > 60) reason = reason.substring(0, 60);
                uniq.put(p.articleId(), row(from.getId(), p.articleId(), type, reason));
                if (uniq.size() >= MAX_RELATED) break;
            }
            return new ArrayList<>(uniq.values());
        } catch (Exception e) {
            log.warn("LLM relation pick failed for {}: {}", from.getId(), e.toString());
            return List.of();
        }
    }

    /** 兜底:按共享标签数量(candidates 已按此排序)取前 MAX_RELATED 篇。 */
    private List<ArticleRelation> fallback(String fromId, List<Article> candidates) {
        return candidates.stream()
            .limit(MAX_RELATED)
            .map(a -> row(fromId, a.getId(), FALLBACK_TYPE, FALLBACK_REASON))
            .collect(Collectors.toList());
    }

    private ArticleRelation row(String fromId, String toId, String type, String reason) {
        ArticleRelation r = new ArticleRelation();
        r.setFromArticleId(fromId);
        r.setToArticleId(toId);
        r.setRelationType(type);
        r.setReason(reason);
        return r;
    }

    private List<RelatedArticleDto> toDtos(List<ArticleRelation> relations) {
        List<String> toIds = relations.stream().map(ArticleRelation::getToArticleId).distinct().toList();
        if (toIds.isEmpty()) return List.of();
        Map<String, Article> byId = articleMapper.selectBatchIds(toIds).stream()
            .collect(Collectors.toMap(Article::getId, a -> a));
        Map<String, List<String>> tagsByArticle = articleQueryService.tagsByArticle(toIds);
        List<RelatedArticleDto> out = new ArrayList<>();
        for (ArticleRelation r : relations) {
            Article a = byId.get(r.getToArticleId());
            if (a == null) continue;  // to-article 已删
            ArticleCardDto card = articleQueryService.toCard(
                a, tagsByArticle.getOrDefault(a.getId(), List.of()));
            out.add(new RelatedArticleDto(card, r.getRelationType(), r.getReason()));
        }
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
