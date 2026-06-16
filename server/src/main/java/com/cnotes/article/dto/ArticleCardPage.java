package com.cnotes.article.dto;

import java.util.List;

/** 收件箱分页结果:当前页卡片 + 全表总数(供前端"加载更多"判断到底)。 */
public record ArticleCardPage(List<ArticleCardDto> items, long total) {
}
