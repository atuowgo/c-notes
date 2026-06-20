package com.cnotes.chat.tool;

import com.cnotes.chat.dto.ChatDiscovery;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 源3:联网搜索工具。作为 Spring AI {@link Tool} 暴露给 ChatClient——当本文(源1)与
 * 知识网(源2)不足以回答时,模型自行决定调用本工具补充实时网页信息。
 *
 * <p><b>三通道,逐级降级</b>(均无需 API key):
 * <ol>
 *   <li>通道1:DuckDuckGo HTML 版端点,jsoup 解析 top N 条「标题 + 摘要 + 真实链接」——
 *       覆盖面最广的通用网页搜索;</li>
 *   <li>通道2:DuckDuckGo Instant Answer JSON API(api.duckduckgo.com)——通道1 被反爬拦截
 *       (返回挑战页、零结果)时回退,从 Abstract / Results / RelatedTopics 抽结果;命中实体时质量高;</li>
 *   <li>通道3:维基百科全文检索 API(<code>list=search</code>)——前两者皆空时的稳健兜底,
 *       对任意中英文长句都能返回真实、可收藏的条目(按问题语言自动选 zh / en 维基)。</li>
 * </ol>
 * <b>优雅降级</b>:任何失败(网络/超时/解析/空结果)一律返回空串、绝不抛异常——源3 不可用
 * 不应使整轮 chat 失败。
 *
 * <p><b>结构化捕获</b>:LLM 工具调用与整轮 chat 在同一线程同步发生,故用 {@code ThreadLocal}
 * 在 {@link #beginCapture()}/{@link #drainCaptured()} 之间记录本轮发现的结构化结果(标题/链接/摘要),
 * 供 {@code ChatService} 取出随回复返回——让「联网发现的好内容可一键收藏」(产品设计 §2)。
 */
@Component
public class WebSearchTool {

    private static final int TIMEOUT_MS = 8_000;
    /** 单轮 chat 最多回给前端的发现条数(LLM 可能多次调用工具,累计去重后截断,避免刷屏)。 */
    private static final int MAX_DISCOVERIES_PER_TURN = 6;
    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final String baseUrl;
    private final String iaUrl;
    private final String wikiUrlTemplate;
    private final int topN;
    private final ObjectMapper om = new ObjectMapper();

    /** 本轮(本线程)联网发现的结构化结果;null 表示未在捕获区间内。 */
    private final ThreadLocal<List<ChatDiscovery>> captured = new ThreadLocal<>();

    @Autowired
    public WebSearchTool(
            @Value("${chat.websearch.base-url:https://html.duckduckgo.com/html/}") String baseUrl,
            @Value("${chat.websearch.ia-url:https://api.duckduckgo.com/}") String iaUrl,
            @Value("${chat.websearch.wiki-url:https://%s.wikipedia.org/w/api.php}") String wikiUrlTemplate,
            @Value("${chat.websearch.top-n:3}") int topN) {
        this.baseUrl = baseUrl;
        this.iaUrl = iaUrl;
        this.wikiUrlTemplate = wikiUrlTemplate;
        this.topN = topN;
    }

    /** 测试便捷构造:默认 IA / 维基端点。 */
    WebSearchTool(String baseUrl, int topN) {
        this(baseUrl, "https://api.duckduckgo.com/", "https://%s.wikipedia.org/w/api.php", topN);
    }

    /** 开启本线程的结构化捕获(每轮 chat 前调用)。 */
    public void beginCapture() {
        captured.set(new ArrayList<>());
    }

    /**
     * 取出并清空本线程捕获的发现(每轮 chat 后调用)。按 url 去重、保序;非捕获区间返回空表。
     */
    public List<ChatDiscovery> drainCaptured() {
        List<ChatDiscovery> list = captured.get();
        captured.remove();
        if (list == null || list.isEmpty()) return List.of();
        Map<String, ChatDiscovery> byUrl = new LinkedHashMap<>();
        for (ChatDiscovery d : list) {
            if (d.url() != null && !d.url().isBlank()) byUrl.putIfAbsent(d.url(), d);
        }
        List<ChatDiscovery> out = new ArrayList<>(byUrl.values());
        return out.size() > MAX_DISCOVERIES_PER_TURN
            ? List.copyOf(out.subList(0, MAX_DISCOVERIES_PER_TURN))
            : List.copyOf(out);
    }

    @Tool(description = "联网搜索互联网获取实时网页信息。当本文与已沉淀的知识不足以回答用户问题时调用,"
            + "返回若干条带标题、摘要与链接的网页结果。")
    public String search(@ToolParam(description = "搜索关键词或问题") String query) {
        if (query == null || query.isBlank()) return "";
        // 通道1:DuckDuckGo HTML(通用网页)。
        List<ChatDiscovery> results = searchHtml(query);
        // 通道2:被拦截/零结果时回退 Instant Answer JSON API(实体答案)。
        if (results.isEmpty()) {
            results = searchInstantAnswer(query);
        }
        // 通道3:仍为空则维基百科全文检索(任意中英文长句的稳健兜底)。
        if (results.isEmpty()) {
            results = searchWikipedia(query);
        }
        recordCaptured(results);
        return format(results);
    }

    /** 主通道:抓 DuckDuckGo HTML 结果页并解析。失败/被拦截返回空表(不抛)。 */
    private List<ChatDiscovery> searchHtml(String query) {
        try {
            String url = baseUrl + "?q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
            return parse(doc.outerHtml());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 备通道:DuckDuckGo Instant Answer JSON API。失败返回空表(不抛)。 */
    private List<ChatDiscovery> searchInstantAnswer(String query) {
        try {
            String url = iaUrl + "?q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&format=json&no_html=1&no_redirect=1";
            String json = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)   // 返回的是 application/json,jsoup 默认只收 HTML
                    .followRedirects(true)
                    .execute()
                    .body();
            return parseInstantAnswer(json);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 通道3:维基百科全文检索。按问题语言选 zh / en 维基。失败返回空表(不抛)。 */
    private List<ChatDiscovery> searchWikipedia(String query) {
        try {
            String lang = hasCjk(query) ? "zh" : "en";
            String api = String.format(wikiUrlTemplate, lang);
            String url = api + "?action=query&list=search&format=json&srlimit=" + topN
                    + "&srsearch=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
            String json = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .execute()
                    .body();
            return parseWikipedia(json, lang);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 纯解析(不发网络),便于测试:从 DuckDuckGo HTML 结果页提取 top N 条文本「标题 / 链接 / 摘要」。 */
    String parseResults(String html) {
        return format(parse(html));
    }

    /**
     * 纯解析维基百科 {@code list=search} JSON(不发网络),便于测试。每条 → 标题 + 条目 URL +
     * 去标签摘要;按 url 去重,截到 top N。失败优雅降级为空表。
     */
    List<ChatDiscovery> parseWikipedia(String json, String lang) {
        try {
            if (json == null || json.isBlank()) return List.of();
            JsonNode search = om.readTree(json).path("query").path("search");
            if (!search.isArray()) return List.of();
            Map<String, ChatDiscovery> byUrl = new LinkedHashMap<>();
            for (JsonNode it : search) {
                String title = text(it, "title");
                if (title.isBlank()) continue;
                String articleUrl = "https://" + lang + ".wikipedia.org/wiki/"
                        + java.net.URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8)
                            .replace("%2F", "/");
                String snippet = stripTags(text(it, "snippet"));
                addTo(byUrl, new ChatDiscovery(title, articleUrl, snippet));
                if (byUrl.size() >= topN) break;
            }
            return List.copyOf(byUrl.values());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 维基摘要含 <span class="searchmatch"> 等高亮标签与 &hellip;,去标签转纯文本。 */
    private String stripTags(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text().trim();
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;   // CJK 统一表意文字
        }
        return false;
    }

    /** 纯解析为结构化结果:top N 条「标题 / 真实链接 / 摘要」,失败优雅降级为空表。 */
    List<ChatDiscovery> parse(String html) {
        try {
            if (html == null || html.isBlank()) return List.of();
            Document doc = Jsoup.parse(html);
            List<ChatDiscovery> items = new ArrayList<>();
            for (Element result : doc.select(".result")) {
                Element a = result.selectFirst("a.result__a");
                if (a == null) continue;
                String title = a.text().trim();
                String link = realLink(a.attr("href"));
                Element snip = result.selectFirst(".result__snippet");
                String snippet = snip == null ? "" : snip.text().trim();
                if (title.isBlank() && snippet.isBlank()) continue;
                items.add(new ChatDiscovery(title, link, snippet));
                if (items.size() >= topN) break;
            }
            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 纯解析 DuckDuckGo Instant Answer JSON(不发网络),便于测试。
     * 依次取:Abstract(权威概览)→ Results(官网)→ RelatedTopics(含嵌套 Topics),
     * 抽「标题 / 链接 / 摘要」,按 url 去重、截到 top N。失败优雅降级为空表。
     */
    List<ChatDiscovery> parseInstantAnswer(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            JsonNode root = om.readTree(json);
            Map<String, ChatDiscovery> byUrl = new LinkedHashMap<>();

            // 1) Abstract:最权威的一条概览。
            String absUrl = text(root, "AbstractURL");
            if (!absUrl.isBlank()) {
                String heading = text(root, "Heading");
                String absText = text(root, "AbstractText");
                String title = !heading.isBlank() ? heading
                        : (!absText.isBlank() ? absText : absUrl);
                addTo(byUrl, new ChatDiscovery(title, absUrl, absText));
            }

            // 2) Results:官方站点等直接结果。
            collectTopicArray(root.get("Results"), byUrl);
            // 3) RelatedTopics:相关条目(可能含嵌套 Topics 分组)。
            collectTopicArray(root.get("RelatedTopics"), byUrl);

            List<ChatDiscovery> out = new ArrayList<>(byUrl.values());
            return out.size() > topN ? out.subList(0, topN) : out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 递归收集 DuckDuckGo Topic 数组里带 FirstURL 的条目(展开嵌套 Topics)。 */
    private void collectTopicArray(JsonNode arr, Map<String, ChatDiscovery> sink) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode it : arr) {
            JsonNode nested = it.get("Topics");
            if (nested != null && nested.isArray()) {
                collectTopicArray(nested, sink);
                continue;
            }
            String url = text(it, "FirstURL");
            if (url.isBlank()) continue;
            String txt = text(it, "Text");
            String title = txt.isBlank() ? url : firstSentence(txt);
            addTo(sink, new ChatDiscovery(title, url, txt));
            if (sink.size() >= topN) return;
        }
    }

    private void addTo(Map<String, ChatDiscovery> sink, ChatDiscovery d) {
        if (d.url() != null && !d.url().isBlank()) sink.putIfAbsent(d.url(), d);
    }

    /** Text 形如「标题 - 描述」;取首段作标题(截断过长)。 */
    private String firstSentence(String text) {
        String t = text.trim();
        int dash = t.indexOf(" - ");
        String head = dash > 0 ? t.substring(0, dash) : t;
        return head.length() > 80 ? head.substring(0, 80) : head;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asString().trim();
    }

    /** 把结构化结果格式化成给 LLM 的纯文本(编号 + 标题 + 链接 + 摘要)。 */
    private String format(List<ChatDiscovery> items) {
        if (items == null || items.isEmpty()) return "";
        List<String> lines = new ArrayList<>();
        for (ChatDiscovery d : items) {
            StringBuilder sb = new StringBuilder();
            sb.append(lines.size() + 1).append(". ").append(nz(d.title()));
            if (d.url() != null && !d.url().isBlank()) sb.append(" <").append(d.url()).append(">");
            if (d.snippet() != null && !d.snippet().isBlank()) sb.append("\n   ").append(d.snippet());
            lines.add(sb.toString());
        }
        return String.join("\n", lines);
    }

    private void recordCaptured(List<ChatDiscovery> results) {
        List<ChatDiscovery> sink = captured.get();
        if (sink != null && results != null) sink.addAll(results);
    }

    /** 仅供测试:在捕获区间内塞入一条发现,模拟 search() 工具调用的副作用。 */
    void captureForTest(ChatDiscovery d) {
        recordCaptured(List.of(d));
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
