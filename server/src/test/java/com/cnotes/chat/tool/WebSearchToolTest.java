package com.cnotes.chat.tool;

import com.cnotes.chat.dto.ChatDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

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
    void parsesStructuredDiscoveriesWithTitleUrlSnippet() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 2);
        String html = """
            <div class="result results_links">
              <div class="result__body">
                <h2 class="result__title"><a class="result__a"
                   href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa&rut=x">红烧牛肉做法</a></h2>
                <a class="result__snippet">先焯水再小火慢炖更入味。</a>
              </div>
            </div>
            """;

        List<ChatDiscovery> out = tool.parse(html);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).title()).isEqualTo("红烧牛肉做法");
        assertThat(out.get(0).url()).isEqualTo("https://example.com/a");
        assertThat(out.get(0).snippet()).isEqualTo("先焯水再小火慢炖更入味。");
    }

    @Test
    void captureCollectsDiscoveriesAndDrainDedupesByUrl() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 5);
        // 未进入捕获区间:drain 返回空表,不抛。
        assertThat(tool.drainCaptured()).isEmpty();

        tool.beginCapture();
        // search() 走真实网络会失败优雅降级,这里直接喂解析结果模拟工具调用的副作用。
        // 通过 parseResults 触发解析路径在别处验证;此处验证 begin/drain 生命周期与去重。
        // 用一段含重复 url 的 HTML 验证按 url 去重:
        String html = """
            <div class="result"><div class="result__body">
              <h2><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fdup.com%2Fx">A</a></h2>
              <a class="result__snippet">第一次</a></div></div>
            <div class="result"><div class="result__body">
              <h2><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fdup.com%2Fx">A 重复</a></h2>
              <a class="result__snippet">第二次</a></div></div>
            """;
        // 模拟 search() 内部的 recordCaptured:解析两条同 url,捕获后 drain 应去重为 1 条。
        tool.parse(html).forEach(d -> tool.captureForTest(d));
        List<ChatDiscovery> drained = tool.drainCaptured();

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).url()).isEqualTo("https://dup.com/x");
        // drain 后再次 drain 为空(已清空)。
        assertThat(tool.drainCaptured()).isEmpty();
    }

    @Test
    void searchSwallowsNetworkFailureReturningEmpty() {
        // 三个通道全部指向不可达主机:源3 整体不可用时应返回空串而非抛异常。
        WebSearchTool tool = new WebSearchTool(
            "https://nonexistent.invalid.host.local/html/",
            "https://nonexistent.invalid.host.local/ia/",
            "https://%s.nonexistent.invalid.host.local/w/api.php",
            5);
        String out = tool.search("任意问题");
        assertThat(out).isEmpty();
    }

    @Test
    void parsesInstantAnswerJsonAbstractResultsAndRelatedTopics() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 5);
        String json = """
            {
              "Heading": "Transformer (machine learning model)",
              "AbstractText": "A transformer is a deep learning architecture based on attention.",
              "AbstractURL": "https://en.wikipedia.org/wiki/Transformer_(machine_learning_model)",
              "Results": [
                {"Text": "TensorFlow Transformer guide", "FirstURL": "https://www.tensorflow.org/text/tutorials/transformer"}
              ],
              "RelatedTopics": [
                {"Text": "Attention (machine learning) - mechanism", "FirstURL": "https://en.wikipedia.org/wiki/Attention_(machine_learning)"},
                {"Name": "grp", "Topics": [
                  {"Text": "BERT - language model", "FirstURL": "https://en.wikipedia.org/wiki/BERT_(language_model)"}
                ]}
              ]
            }
            """;

        List<ChatDiscovery> out = tool.parseInstantAnswer(json);

        // Abstract 优先,且各通道按 url 去重合并。
        assertThat(out).isNotEmpty();
        assertThat(out.get(0).title()).isEqualTo("Transformer (machine learning model)");
        assertThat(out.get(0).url()).contains("en.wikipedia.org/wiki/Transformer");
        List<String> urls = out.stream().map(ChatDiscovery::url).toList();
        assertThat(urls).contains("https://www.tensorflow.org/text/tutorials/transformer");
        assertThat(urls).contains("https://en.wikipedia.org/wiki/Attention_(machine_learning)");
        // 嵌套 Topics 被展开。
        assertThat(urls).contains("https://en.wikipedia.org/wiki/BERT_(language_model)");
        // 「标题 - 描述」取首段作标题。
        ChatDiscovery bert = out.stream().filter(d -> d.url().contains("BERT")).findFirst().orElseThrow();
        assertThat(bert.title()).isEqualTo("BERT");
    }

    @Test
    void instantAnswerEmptyOrMalformedDegradesToEmptyList() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 5);
        assertThat(tool.parseInstantAnswer("")).isEmpty();
        assertThat(tool.parseInstantAnswer("not-json")).isEmpty();
        assertThat(tool.parseInstantAnswer("{}")).isEmpty();
    }

    @Test
    void parsesWikipediaSearchJsonStrippingHighlightTags() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 3);
        String json = """
            {"query":{"search":[
              {"title":"Transformer (deep learning)",
               "snippet":"A <span class=\\"searchmatch\\">transformer</span> is a deep learning architecture&hellip;"},
              {"title":"Vision transformer",
               "snippet":"applies the <span class=\\"searchmatch\\">transformer</span> to images"}
            ]}}
            """;

        List<ChatDiscovery> out = tool.parseWikipedia(json, "en");

        assertThat(out).hasSize(2);
        assertThat(out.get(0).title()).isEqualTo("Transformer (deep learning)");
        // 空格转下划线,parens 被百分号编码,仍指向真实维基条目。
        assertThat(out.get(0).url()).startsWith("https://en.wikipedia.org/wiki/Transformer");
        // 高亮标签被去除,纯文本摘要。
        assertThat(out.get(0).snippet()).contains("deep learning architecture");
        assertThat(out.get(0).snippet()).doesNotContain("<span");
        assertThat(out.get(1).url()).contains("Vision_transformer");
    }

    @Test
    void wikipediaEmptyOrMalformedDegradesToEmptyList() {
        WebSearchTool tool = new WebSearchTool("https://html.duckduckgo.com/html/", 3);
        assertThat(tool.parseWikipedia("", "en")).isEmpty();
        assertThat(tool.parseWikipedia("not-json", "en")).isEmpty();
        assertThat(tool.parseWikipedia("{\"query\":{\"search\":[]}}", "en")).isEmpty();
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
