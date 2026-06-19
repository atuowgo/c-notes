package com.cnotes.tag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 簇合并重定向:from_tag_id 已并入 to_tag_id;归类时把指向 from 的模型标签改投 to(纠偏 V5)。 */
@Data
@TableName("tag_merge")
public class TagMerge {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String fromTagId;
    private String toTagId;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
