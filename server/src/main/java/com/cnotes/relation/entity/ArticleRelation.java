package com.cnotes.relation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("article_relation")
public class ArticleRelation {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String fromArticleId;
    private String toArticleId;
    private String relationType;   // 同概念/互补/对立/延伸/相关
    private String reason;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
