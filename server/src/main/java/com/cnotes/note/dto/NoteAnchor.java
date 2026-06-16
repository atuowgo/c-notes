package com.cnotes.note.dto;

/** 正文定位锚点:目前用正文字符偏移 [start, end);预留 selector 等以便将来抗重排。 */
public record NoteAnchor(Integer start, Integer end) {}
