package com.cnotes.chat.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 源3(联网搜索)@Tool 的测试。
 * 离线:用 DuckDuckGo HTML 版页面结构的固定样本断言解析(标题/摘要/真实链接解码、topN 截断);
 * 并断言「空/异常 HTML 优雅降级返回空串不抛」「网络失败返回空串不抛」——源3 不可用不应使整轮 chat 失败。
 * 门控真实网络:WEBSEARCH_LIVE=1 时真打 DuckDuckGo 验证返回非空。
 */
class WebSearchToolTest {

    @Test
    void parsesDuckDuckGoHtmlResultsDecodingRealLinksAndTopN() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 2);
        String html = """
            <div class="result results_links">
              <div class="result__body">
                <h2 class="result__title"><a class="result__a"
                   href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa&rut=x">红烧牛肉做法</a></h2>
                <a class="result__snippet">先焯水再小火慢炖更入味。</a>
              </div>
            </div>
            <div class="result results_links">
              <div class="result__body">
                <h2 class="result__title"><a class="result__a"
                   href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fb">第二条结果</a></h2>
                <a class="result__snippet">第二条摘要。</a>
              </div>
            </div>
            <div class="result results_links">
              <div class="result__body">
                <h2 class="result__title"><a class="result__a"
                   href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fc">第三条超出topN</a></h2>
                <a class="result__snippet">不应出现。</a>
              </div>
            </div>
            """;

        String out = tool.parseResults(html);

        assertThat(out).contains("红烧牛肉做法");
        assertThat(out).contains("https://example.com/a");   // uddg 跳转包装被解码为真实链接
        assertThat(out).contains("先焯水再小火慢炖更入味。");
        assertThat(out).contains("第二条结果");
        assertThat(out).doesNotContain("第三条超出topN");      // topN=2 截断
        assertThat(out).doesNotContain("uddg");               // 不泄露 DuckDuckGo 跳转包装
    }

    @Test
    void malformedOrEmptyHtmlDegradesToEmptyString() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 5);
        assertThat(tool.parseResults("")).isEmpty();
        assertThat(tool.parseResults("<html><body>无结果</body></html>")).isEmpty();
    }

    @Test
    void searchSwallowsNetworkFailureReturningEmpty() {
        WebSearchTool tool = new WebSearchTool("https://nonexistent.invalid.host.local/html/", 5);
        String out = tool.search("任意问题");
        assertThat(out).isEmpty();   // 源3 不可用:返回空串而非抛异常
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WEBSEARCH_LIVE", matches = "1")
    void realDuckDuckGoReturnsResults() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 3);
        String out = tool.search("Spring Boot framework");
        assertThat(out).isNotBlank();
        assertThat(out).containsIgnoringCase("http");
    }
}
