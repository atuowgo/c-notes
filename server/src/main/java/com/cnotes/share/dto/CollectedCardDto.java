package com.cnotes.share.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收录卡片(渲染进收件箱,带「收录自 X」角标)。
 * 原文撤回(删除)或转为私有时 sourceWithdrawn=true,仅保留个人笔记。
 */
@Data
public class CollectedCardDto {
    /** 收录记录 id(稳定列表 key)。 */
    private String id;
    /** 源文章 id;撤回时为 null。 */
    private String articleId;
    private String title;
    private String summary;
    private String author;
    private String sourceType;
    private List<String> tags;

    /** 源作者昵称。 */
    private String collectedFrom;
    private String personalNote;
    private boolean sourceWithdrawn;
    /** 收录时间。 */
    private LocalDateTime createTime;
}
