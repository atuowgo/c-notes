package com.cnotes.note.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("note_relation")
public class NoteRelation {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String fromNoteId;
    private String toNoteId;
    private String relationType;   // 呼应/对立/延伸/同主题/同篇
    private String reason;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
