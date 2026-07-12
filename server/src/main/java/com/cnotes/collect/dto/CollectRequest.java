package com.cnotes.collect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CollectRequest {
    @NotBlank private String url;
    private String title;
    private String author;
    private String content;       // 插件本地 Readability 提取的纯文本/MD
    private String contentHtml;   // 插件 Readability 干净 HTML(供沉浸式渲染)
    private String sourceType;    // 缺省 browser
}
