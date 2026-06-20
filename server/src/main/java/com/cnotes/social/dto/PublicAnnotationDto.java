package com.cnotes.social.dto;

import com.cnotes.note.dto.NoteAnchor;
import lombok.Data;
import java.time.LocalDateTime;

/** 公开批注(他人文章上、所有人可见的 note.visibility=PUBLIC)。 */
@Data
public class PublicAnnotationDto {
    private String id;
    private String quote;
    private String thought;
    private NoteAnchor anchor;
    private String authorId;
    private String authorNickname;
    private boolean mine;
    private LocalDateTime createTime;
}
