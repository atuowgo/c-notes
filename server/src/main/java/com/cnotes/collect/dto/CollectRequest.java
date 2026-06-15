package com.cnotes.collect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CollectRequest {
    @NotBlank private String url;
    private String title;
    private String author;
    private String content;       // 插件本地 Readability 提取的正文
    private String domSnapshot;   // 兜底(后续抓取计划用)
    private String sourceType;    // 缺省 browser
}
