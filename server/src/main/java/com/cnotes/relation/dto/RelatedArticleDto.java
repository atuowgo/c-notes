package com.cnotes.relation.dto;

import com.cnotes.article.dto.ArticleCardDto;

public record RelatedArticleDto(ArticleCardDto article, String relationType, String reason) {}
