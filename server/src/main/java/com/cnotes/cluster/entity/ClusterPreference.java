package com.cnotes.cluster.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 簇纠偏审计:记录用户对标签簇的 merge/split/move 操作(谁把源簇导向目标簇)。
 * 仅审计,不驱动业务逻辑;由 {@link com.cnotes.cluster.ClusterService} 在每次纠偏时插入一行。
 * owner_id 隔离(A1):记录操作者;可空(测试 permitAll / 历史无主数据)。
 */
@Data
@TableName("cluster_preference")
public class ClusterPreference {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String ownerId;     // 操作者用户 id;可空
    private String action;      // merge/split/move
    private String sourceId;    // 源簇(标签)id
    private String targetId;    // 目标簇(标签)id;split 为新建簇
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
