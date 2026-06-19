package com.cnotes.note.dto;

import lombok.Data;

@Data
public class RelatedNoteDto {
    private NoteDto note;
    private String relationType;
    private String reason;
}
