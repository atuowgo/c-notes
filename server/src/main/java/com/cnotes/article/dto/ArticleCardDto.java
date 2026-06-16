package com.cnotes.article.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleCardDto {
    private String id;
    private String title;
    private String author;
    private String sourceType;
    private String summary;
    private String status;
    private LocalDateTime createTime;
    private List<String> tags;
}
