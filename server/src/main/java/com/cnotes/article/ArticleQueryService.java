package com.cnotes.article;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cnotes.article.dto.*;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.entity.Tag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import com.cnotes.tag.mapper.TagMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleQueryService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper om;

    /** 分页收件箱:page 从 1 起,size 默认调用方给定;此处兜底夹取到 [1,100]。 */
    public ArticleCardPage listInbox(int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        Page<Article> pg = articleMapper.selectPage(
            Page.of(p, s),
            Wrappers.<Article>lambdaQuery().orderByDesc(Article::getCreateTime));
        List<Article> articles = pg.getRecords();
        Map<String, List<String>> tagsByArticle =
            tagsByArticle(articles.stream().map(Article::getId).toList());
        List<ArticleCardDto> items = articles.stream()
            .map(a -> toCard(a, tagsByArticle.getOrDefault(a.getId(), List.of())))
            .toList();
        return new ArticleCardPage(items, pg.getTotal());
    }

    public ArticleDetailDto detail(String id) {
        Article a = articleMapper.selectById(id);
        if (a == null) return null;
        ArticleDetailDto d = new ArticleDetailDto();
        d.setId(a.getId()); d.setTitle(a.getTitle()); d.setAuthor(a.getAuthor());
        d.setSummary(a.getSummary()); d.setContent(a.getContent()); d.setStatus(a.getStatus());
        d.setSourceType(a.getSourceType()); d.setUrl(a.getUrl());
        d.setKeyPoints(parsePoints(a.getKeyPoints()));
        d.setTags(tagsByArticle(List.of(a.getId())).getOrDefault(a.getId(), List.of()));
        return d;
    }

    /** 批量查若干文章的标签名,按 article_id 聚合(读已有 article_tag/tag,不触碰归类写入)。 */
    public Map<String, List<String>> tagsByArticle(List<String> articleIds) {
        if (articleIds.isEmpty()) return Map.of();
        List<ArticleTag> links = articleTagMapper.selectList(
            Wrappers.<ArticleTag>lambdaQuery().in(ArticleTag::getArticleId, articleIds));
        if (links.isEmpty()) return Map.of();
        Set<String> tagIds = links.stream().map(ArticleTag::getTagId).collect(Collectors.toSet());
        Map<String, String> nameById = tagMapper
            .selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getId, tagIds)).stream()
            .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return links.stream()
            .filter(l -> nameById.containsKey(l.getTagId()))
            .collect(Collectors.groupingBy(
                ArticleTag::getArticleId,
                Collectors.mapping(l -> nameById.get(l.getTagId()), Collectors.toList())));
    }

    private List<String> parsePoints(String json) {
        try { return json == null ? List.of() : om.readValue(json, new TypeReference<List<String>>(){}); }
        catch (Exception e) { return List.of(); }
    }

    public ArticleCardDto toCard(Article a, List<String> tags) {
        ArticleCardDto c = new ArticleCardDto();
        c.setId(a.getId()); c.setTitle(a.getTitle()); c.setAuthor(a.getAuthor());
        c.setSourceType(a.getSourceType()); c.setSummary(a.getSummary());
        c.setStatus(a.getStatus()); c.setCreateTime(a.getCreateTime());
        c.setTags(tags);
        return c;
    }
}
