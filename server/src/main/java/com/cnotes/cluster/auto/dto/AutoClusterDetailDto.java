package com.cnotes.cluster.auto.dto;

import com.cnotes.article.dto.ArticleCardDto;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 语义簇详情:簇元信息 + 成员文章卡片(对齐标签簇 ClusterDetailDto 的 articles 字段)。
 */
@Data
public class AutoClusterDetailDto {
    private String id;
    private String title;
    private int memberCount;
    private String summary;
    private LocalDateTime createTime;
    private List<ArticleCardDto> articles;
}
