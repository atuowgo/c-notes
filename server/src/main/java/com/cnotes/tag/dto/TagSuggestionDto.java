package com.cnotes.tag.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TagSuggestionDto {
    private String id;
    private String articleId;
    private String name;
    private BigDecimal confidence;
    private String status;
    private LocalDateTime createTime;
}
