package com.cnotes.cluster.dto;

import com.cnotes.article.dto.ArticleCardDto;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ClusterDetailDto {
    private String id;
    private String name;
    private String description;
    private String livingSummary;
    private LocalDateTime summaryUpdatedAt;
    private int articleCount;
    private List<ArticleCardDto> articles;
}
