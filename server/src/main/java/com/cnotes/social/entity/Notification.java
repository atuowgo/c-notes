package com.cnotes.social.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;       // 接收者
    private String type;         // LIKE/COMMENT/REPLY/FOLLOW/ANNOTATION
    private String actorId;      // 触发者
    private String articleId;
    private String commentId;
    @TableField("is_read")
    private Boolean read;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
