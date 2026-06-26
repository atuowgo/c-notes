package com.cnotes.link.dto;

import com.cnotes.article.dto.ArticleCardDto;
import lombok.Data;

/**
 * 关联推荐返回项(对齐前端 ArticleLink):目标文章卡片 + 关系类型 + 理由 + 相似度。
 */
@Data
public class ArticleLinkDto {
    private ArticleCardDto targetArticle;   // 关联目标文章卡片
    private String linkType;                // 相关/更深入/对立/互补
    private String reason;                  // 为什么相关(AI 生成;无 key 时空串)
    private Double score;                   // embedding cosine 相似度 0~1
}
