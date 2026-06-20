package com.cnotes.social.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 通知。type:LIKE/COMMENT/REPLY/FOLLOW/ANNOTATION。 */
@Data
public class NotificationDto {
    private String id;
    private String type;
    private String actorId;
    private String actorNickname;
    private String actorAvatarUrl;
    private String articleId;
    private String articleTitle;
    private String commentId;
    private boolean read;
    private LocalDateTime createTime;
}
