package com.cnotes.article.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String ownerId;       // 所有者 app_user.id(多用户隔离)
    private String url;
    private String urlHash;
    private String title;
    private String author;
    private String sourceType;
    private String content;
    private String domSnapshot;    // 渲染后 DOM 快照(插件提交),二级抓取(模型清洗)兜底用
    private String summary;
    private String keyPoints;     // JSON 字符串,Service 层序列化
    private String status;
    private String extractMethod;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime processedAt;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
