package com.cnotes.extract;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContentFetcherTest {

    // 无头默认不触发:提取类用例用一个永远返回空的 renderer。
    private final HeadlessRenderer noHeadless = mock(HeadlessRenderer.class);
    private final ContentFetcher fetcher = new ContentFetcher(noHeadless, 200);

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

    @Test
    void thinHttpResultFallsBackToHeadlessRender() {
        String richBody = "这是无头浏览器渲染后才出现的动态正文,内容足够长。".repeat(8);
        String renderedHtml = "<html><body><article><p>" + richBody + "</p></article></body></html>";
        HeadlessRenderer renderer = mock(HeadlessRenderer.class);
        when(renderer.render(any())).thenReturn(Optional.of(renderedHtml));
        ContentFetcher cf = new ContentFetcher(renderer, 200);

        // HTTP 只拿到很薄的占位内容 → 触发无头兜底
        ContentFetcher.Extracted thin = new ContentFetcher.Extracted("占位", "加载中…");
        ContentFetcher.Extracted out = cf.resolve("https://spa.example.com/x", thin);

        assertThat(out.text()).contains("无头浏览器渲染后才出现");
        verify(renderer).render(any());
    }

    @Test
    void richHttpResultSkipsHeadless() {
        HeadlessRenderer renderer = mock(HeadlessRenderer.class);
        ContentFetcher cf = new ContentFetcher(renderer, 200);
        ContentFetcher.Extracted rich =
            new ContentFetcher.Extracted("标题", "足够长的正文。".repeat(40));

        ContentFetcher.Extracted out = cf.resolve("https://e.com/x", rich);

        assertThat(out).isSameAs(rich);
        verifyNoInteractions(renderer);   // 够厚不触发渲染
    }
}
