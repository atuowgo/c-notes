package com.cnotes.article.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailDto extends ArticleCardDto {
    private String content;
    private List<String> keyPoints;
}
