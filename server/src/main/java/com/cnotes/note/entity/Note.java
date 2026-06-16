package com.cnotes.note.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("note")
public class Note {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String articleId;
    private String quote;
    private String thought;
    private String anchor;        // JSON 文本 {start,end},Service 层(反)序列化
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
