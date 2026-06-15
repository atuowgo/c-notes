package com.cnotes.article.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ArticleCardDto {
    private String id;
    private String title;
    private String author;
    private String sourceType;
    private String summary;
    private String status;
    private LocalDateTime createTime;
}
