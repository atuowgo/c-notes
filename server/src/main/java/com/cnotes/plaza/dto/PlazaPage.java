package com.cnotes.plaza.dto;

import java.util.List;

/** 广场分页结果:当前页卡片 + 候选总数(供前端分页)。 */
public record PlazaPage(List<PlazaCardDto> items, long total) {}
