package com.cnotes.cluster.auto.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 语义簇(自动聚类):复用 Ark embedding 对当前 owner 的 done 文章做 cosine
 * 凝聚层次聚类产出的簇。与标签簇({@code tag})不同——无人工标签,纯向量相似度成簇。
 * owner_id 隔离(A1):每个用户独立聚类。
 */
@Data
@TableName("auto_cluster")
public class AutoCluster {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String ownerId;          // 所有者用户 id;可空(历史/无主数据)
    private String title;            // 簇标题(取 medoid 文章标题)
    private Integer memberCount;     // 成员文章数
    private String summary;          // AI 织的语义综述;可空
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
