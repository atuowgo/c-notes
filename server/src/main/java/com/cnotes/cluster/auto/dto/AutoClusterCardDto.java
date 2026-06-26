package com.cnotes.cluster.auto.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 语义簇卡片(列表用):与标签簇 {@link com.cnotes.cluster.dto.ClusterCardDto} 并列,
 * 区分来源——本类由 embedding 自动聚类产出。
 */
@Data
public class AutoClusterCardDto {
    private String id;
    private String title;
    private int memberCount;
    private String summary;          // 可空;前端按 hasSummary 折叠展示
    private boolean hasSummary;
    private LocalDateTime createTime;
}
