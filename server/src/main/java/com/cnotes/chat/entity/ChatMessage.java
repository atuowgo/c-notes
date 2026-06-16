package com.cnotes.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String sessionId;
    private String role;          // user / assistant
    private String content;
    private String sources;       // JSON 文本 [{type,...}],Service 层(反)序列化
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
