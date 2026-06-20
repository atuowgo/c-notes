package com.cnotes.share.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("bookmark")
public class Bookmark {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String articleId;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
