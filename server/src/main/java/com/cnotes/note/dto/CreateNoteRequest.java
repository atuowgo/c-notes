package com.cnotes.note.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateNoteRequest {
    @NotBlank private String articleId;
    @NotBlank private String quote;
    private String thought;        // 可空:仅划线、不写想法
    private NoteAnchor anchor;
}
