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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)   // 落盘置空需真正写 NULL,绕开默认 NOT_NULL 策略吞掉 null 更新
    private String content;
    private String contentObjectKey;   // 长正文落盘 key(A2);空=正文存 content 列
    private String htmlObjectKey;   // 净化 HTML 落盘 key;空=无 HTML 走降级
    @TableField(exist = false)          // 瞬态:承载入库前/读回后的 HTML,不持久化到列
    private String contentHtml;
    private String summary;
    private String keyPoints;     // JSON 字符串,Service 层序列化
    private String status;
    private String extractMethod;
    private Integer retryCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)   // 达上限置 null 需真正写 NULL,绕开默认 NOT_NULL 策略吞掉 null 更新
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime processedAt;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
