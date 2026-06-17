package com.cnotes.chat.tool;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 源3:联网搜索工具。作为 Spring AI {@link Tool} 暴露给 ChatClient——当本文(源1)与
 * 知识网(源2)不足以回答时,模型自行决定调用本工具补充实时网页信息。
 *
 * <p>实现走 DuckDuckGo 的 HTML 版端点(无需 API key),jsoup 解析 top N 条「标题 + 摘要 + 真实链接」。
 * <b>优雅降级</b>:任何失败(网络/超时/解析/空结果)一律返回空串、绝不抛异常——源3 不可用
 * 不应使整轮 chat 失败。
 */
@Component
public class WebSearchTool {

    private static final int TIMEOUT_MS = 8_000;
    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final String baseUrl;
    private final int topN;

    public WebSearchTool(
            @Value("${chat.websearch.base-url:https://html.duckduckgo.com/html/}") String baseUrl,
            @Value("${chat.websearch.top-n:3}") int topN) {
        this.baseUrl = baseUrl;
        this.topN = topN;
    }

    @Tool(description = "联网搜索互联网获取实时网页信息。当本文与已沉淀的知识不足以回答用户问题时调用,"
            + "返回若干条带标题、摘要与链接的网页结果。")
    public String search(@ToolParam(description = "搜索关键词或问题") String query) {
        try {
            if (query == null || query.isBlank()) return "";
            String url = baseUrl + "?q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
            return parseResults(doc.outerHtml());
        } catch (Exception e) {
            return "";   // 优雅降级:源3 不可用不抛
        }
    }

    /** 纯解析(不发网络),便于测试:从 DuckDuckGo HTML 结果页提取 top N 条「标题 / 链接 / 摘要」。 */
    String parseResults(String html) {
        try {
            if (html == null || html.isBlank()) return "";
            Document doc = Jsoup.parse(html);
            List<String> items = new ArrayList<>();
            for (Element result : doc.select(".result")) {
                Element a = result.selectFirst("a.result__a");
                if (a == null) continue;
                String title = a.text().trim();
                String link = realLink(a.attr("href"));
                Element snip = result.selectFirst(".result__snippet");
                String snippet = snip == null ? "" : snip.text().trim();
                if (title.isBlank() && snippet.isBlank()) continue;
                StringBuilder sb = new StringBuilder();
                sb.append(items.size() + 1).append(". ").append(title);
                if (!link.isBlank()) sb.append(" <").append(link).append(">");
                if (!snippet.isBlank()) sb.append("\n   ").append(snippet);
                items.add(sb.toString());
                if (items.size() >= topN) break;
            }
            return String.join("\n", items);
        } catch (Exception e) {
            return "";
        }
    }

    /** DuckDuckGo 把目标链接包成 //duckduckgo.com/l/?uddg=<编码真实URL>;解出真实 URL。 */
    private String realLink(String href) {
        if (href == null || href.isBlank()) return "";
        try {
            int i = href.indexOf("uddg=");
            if (i < 0) return href.startsWith("//") ? "https:" + href : href;
            String enc = href.substring(i + 5);
            int amp = enc.indexOf('&');
            if (amp >= 0) enc = enc.substring(0, amp);
            return URLDecoder.decode(enc, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
