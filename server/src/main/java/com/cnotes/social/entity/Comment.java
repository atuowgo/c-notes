package com.cnotes.social.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String articleId;
    private String authorId;
    private String parentId;   // NULL 为顶层;否则指向顶层评论 id
    private String body;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
