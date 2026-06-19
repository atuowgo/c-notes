package com.cnotes.cluster.dto;

import lombok.Data;

@Data
public class MoveArticleRequest {
    private String articleId;
    private String toClusterId;
}
