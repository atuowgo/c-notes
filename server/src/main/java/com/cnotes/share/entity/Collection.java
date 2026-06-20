package com.cnotes.share.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 收录:链接引用 + 个人笔记,不深拷贝原文。原文撤回时收录方见占位 + 保留 personalNote。 */
@Data
@TableName("collection")
public class Collection {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String sourceArticleId;
    private String personalNote;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
