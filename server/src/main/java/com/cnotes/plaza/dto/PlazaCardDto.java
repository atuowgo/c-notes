package com.cnotes.plaza.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 广场卡片:文章公开信息 + 作者 + 行为计数 + 质量分。 */
@Data
public class PlazaCardDto {
    private String id;
    private String title;
    private String author;
    private String summary;
    private String sourceType;
    private LocalDateTime createTime;
    private List<String> tags;

    private String ownerId;
    private String ownerNickname;
    private String ownerAvatarUrl;

    private long bookmarkCount;
    private long collectCount;
    private long likeCount;       // 阶段 4 启用,暂 0
    private long commentCount;    // 阶段 4 启用,暂 0

    /** 质量分(四舍五入展示);hover 可看构成(前端按计数自行说明)。 */
    private int qualityScore;
    private String effectiveShareLevel;
}
