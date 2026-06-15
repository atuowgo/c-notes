package com.cnotes.article;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.dto.*;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleQueryService {

    private final ArticleMapper articleMapper;
    private final ObjectMapper om;

    public List<ArticleCardDto> listInbox() {
        return articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .orderByDesc(Article::getCreateTime))
            .stream().map(this::toCard).toList();
    }

    public ArticleDetailDto detail(String id) {
        Article a = articleMapper.selectById(id);
        if (a == null) return null;
        ArticleDetailDto d = new ArticleDetailDto();
        d.setId(a.getId()); d.setTitle(a.getTitle()); d.setAuthor(a.getAuthor());
        d.setSummary(a.getSummary()); d.setContent(a.getContent()); d.setStatus(a.getStatus());
        d.setSourceType(a.getSourceType());
        d.setKeyPoints(parsePoints(a.getKeyPoints()));
        return d;
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
