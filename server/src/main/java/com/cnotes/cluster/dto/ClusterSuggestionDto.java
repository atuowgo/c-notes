package com.cnotes.cluster.dto;

import com.cnotes.article.dto.ArticleCardDto;
import lombok.Data;
import java.util.List;

/** 语义建议簇:LLM 提议的、现有标签未覆盖的新主题分组。 */
@Data
public class ClusterSuggestionDto {
    private String name;
    private String reason;
    private List<ArticleCardDto> articles;
}
