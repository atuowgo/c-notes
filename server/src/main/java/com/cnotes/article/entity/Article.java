package com.cnotes.article.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String ownerId;      // 所有者用户 id(A1 隔离);可空(历史/wechat 无主数据)
    private String url;
    private String urlHash;
    private String title;
    private String author;
    private String sourceType;
    private String content;
    private String contentObjectKey;   // 长正文落盘 key(A2);空=正文存 content 列
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
