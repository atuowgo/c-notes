package com.cnotes.note.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NoteDto {
    private String id;
    private String articleId;
    private String articleTitle;   // 关联文章标题,用于"全部想法"跨文章展示
    private String quote;
    private String thought;
    private NoteAnchor anchor;
    private LocalDateTime createTime;
}
