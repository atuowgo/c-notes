package com.cnotes.article.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailDto extends ArticleCardDto {
    private String url;
    private String content;
    private List<String> keyPoints;
    /** 逐篇覆盖的分享级别;null 表示继承账号默认。 */
    private String shareLevel;
    /** 生效分享级别 = shareLevel ?? 账号默认(供阅读页分享控件回显)。 */
    private String effectiveShareLevel;
}
