package com.cnotes.article.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailDto extends ArticleCardDto {
    private String url;
    private String content;
    private String contentHtml;   // 净化后正文 HTML(沉浸式渲染);无则为 null,前端降级
    private List<String> keyPoints;
}
