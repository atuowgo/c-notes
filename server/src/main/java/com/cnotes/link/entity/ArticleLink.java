package com.cnotes.link.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文章关联推荐:为某文章算出的"相关"文章(候选=共享标签,Ark embedding cosine 排序,top-N)。
 * link_type 取值:相关/更深入/对立/互补(本批次算法只产"相关",余者预留)。
 * reason 由 ChatClient(DeepSeek)生成"为什么相关"短句;无 key 优雅降级空串。
 * owner_id 隔离(A1):每个用户独立关联图;可空(兼容历史/无主数据)。
 */
@Data
@TableName("article_link")
public class ArticleLink {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String ownerId;           // 所有者用户 id;可空(历史/无主数据)
    private String articleId;         // 源文章 id
    private String targetArticleId;   // 关联目标文章 id
    private String linkType;          // 相关/更深入/对立/互补
    private String reason;            // AI 生成"为什么相关"短句;无 key 时空串
    private Double score;             // embedding cosine 相似度 0~1
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
