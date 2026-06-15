package com.cnotes.tag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("article_tag")
public class ArticleTag {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String articleId;
    private String tagId;
    private BigDecimal confidence;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
