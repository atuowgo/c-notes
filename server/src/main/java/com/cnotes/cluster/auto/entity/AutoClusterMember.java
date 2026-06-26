package com.cnotes.cluster.auto.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 语义簇成员:簇→文章的多对一关联(一个簇含若干文章)。
 * 用 ASSIGN_UUID 主键(与 article_tag 风格一致),便于 MyBatis-Plus 单行删除/更新。
 */
@Data
@TableName("auto_cluster_member")
public class AutoClusterMember {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String clusterId;
    private String articleId;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
