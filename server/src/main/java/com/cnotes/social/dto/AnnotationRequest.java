package com.cnotes.social.dto;

import com.cnotes.note.dto.NoteAnchor;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发表公开批注:划选 quote + 想法 + 锚点偏移。 */
@Data
public class AnnotationRequest {
    @NotBlank private String quote;
    private String thought;
    private NoteAnchor anchor;
}
