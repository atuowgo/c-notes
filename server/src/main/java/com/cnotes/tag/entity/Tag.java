package com.cnotes.tag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tag")
public class Tag {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String ownerId;                 // 标签所有者 app_user.id(私有标签池)
    private String name;
    private String description;
    private String livingSummary;          // 演进式综述(知识网 V3)
    private Integer summaryMemberCount;     // 上次生成综述时的成员文章数
    private LocalDateTime summaryUpdatedAt;
    private Boolean archived;               // 合并后的源簇归档(纠偏 V5)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
