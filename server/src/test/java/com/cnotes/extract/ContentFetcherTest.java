package com.cnotes.extract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentFetcherTest {

    private final ContentFetcher fetcher = new ContentFetcher();

    @Test
    void extractsWeChatArticleBodyAndTitle() {
        String html = "<html><head><title>页面 title 标签</title></head><body>"
            + "<h1 id=\"activity-name\">微信文章真实标题</h1>"
            + "<div id=\"js_content\">"
            + "<p>第一段正文,讲清楚了文章的核心观点与背景。</p>"
            + "<p>第二段补充了另一个要点和例子。</p>"
            + "</div>"
            + "<div id=\"comments\"><p>无关评论区</p></div>"
            + "</body></html>";

        ContentFetcher.Extracted ex = fetcher.extractHtml("https://mp.weixin.qq.com/s/abc", html);

        assertThat(ex.title()).isEqualTo("微信文章真实标题");
        assertThat(ex.text()).contains("第一段正文").contains("第二段补充");
        assertThat(ex.text()).doesNotContain("无关评论区");   // 只取 #js_content
        assertThat(ex.text()).contains("\n\n");               // 段落以空行分隔
    }

    @Test
    void genericArticleFallbackUsesArticleTag() {
        String body = "这是正文段落,需要足够长才能被当作主要内容来提取。".repeat(5);
        String html = "<html><head><title>博客标题</title></head><body>"
            + "<nav>导航</nav>"
            + "<article><h1>文章 H1 标题</h1><p>" + body + "</p></article>"
            + "</body></html>";

        ContentFetcher.Extracted ex = fetcher.extractHtml("https://example.com/post", html);

        assertThat(ex.title()).isEqualTo("文章 H1 标题");
        assertThat(ex.text()).contains("这是正文段落");
        assertThat(ex.text()).doesNotContain("导航");
    }
}
