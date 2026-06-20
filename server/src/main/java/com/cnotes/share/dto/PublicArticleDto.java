package com.cnotes.share.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开文章视图(匿名只读)。仅暴露公开安全字段:
 * 不含 domSnapshot / lastError / 内部状态机字段,避免泄露私有信息。
 */
@Data
public class PublicArticleDto {
    private String id;
    private String title;
    private String author;
    private String summary;
    private String content;
    private String url;
    private List<String> keyPoints;
    private List<String> tags;
    private LocalDateTime createTime;

    /** 来源作者(发布者)展示信息。 */
    private String ownerId;
    private String ownerNickname;
    private String ownerAvatarUrl;

    /** 生效分享级别(决定前端渐进显示哪些操作)。 */
    private String effectiveShareLevel;

    /** 当前查看者(若已登录)对本文的互动态。 */
    private boolean bookmarked;
    private boolean collected;
    /** 是否本人文章(本人无需对自己收藏/收录)。 */
    private boolean mine;
}
