package com.cnotes.cluster.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClusterCardDto {
    private String id;
    private String name;
    private String description;
    private int articleCount;
    private boolean hasSummary;
    private LocalDateTime summaryUpdatedAt;
}
