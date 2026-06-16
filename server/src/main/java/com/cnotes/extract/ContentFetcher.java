package com.cnotes.extract;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端正文抓取(三级抓取的服务器侧:处理裸 URL、微信公众号文章)。
 *
 * <p>当前实现 = 一次 HTTP 抓取(jsoup)+ 启发式正文提取。微信公众号文章页是
 * <b>服务端渲染的静态 HTML</b>(正文在 {@code #js_content}),普通抓取即可拿全;
 * 强动态页(需登录/JS 渲染)留待无头浏览器后续硬化。
 */
@Service
public class ContentFetcher {

    public record Extracted(String title, String text) {}

    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BODY = 5 * 1024 * 1024;
    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    /** 抓取并提取;失败(网络/超时/解析)返回 null,由上层决定重试。 */
    public Extracted fetch(String url) {
        try {
            Document doc = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_BODY)
                .followRedirects(true)
                .get();
            return extract(doc);
        } catch (Exception e) {
            return null;
        }
    }

    /** 纯提取(不发网络),便于测试。 */
    public Extracted extractHtml(String url, String html) {
        return extract(Jsoup.parse(html, url));
    }

    private Extracted extract(Document doc) {
        String title = pickTitle(doc);   // 先取标题,再剥离页面 chrome
        doc.select("script, style, noscript, nav, aside, form, [role=navigation], #mw-navigation, .mw-jump-link").remove();
        Element root = pickContentRoot(doc);
        String text = blocksToText(root);
        return new Extracted(title, text);
    }

    private String pickTitle(Document doc) {
        // 微信公众号标题
        Element wx = doc.selectFirst("#activity-name, h1.rich_media_title");
        if (wx != null && !wx.text().isBlank()) return wx.text().trim();
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) return h1.text().trim();
        String t = doc.title();
        return t == null || t.isBlank() ? null : t.trim();
    }

    // 常见正文容器(微信 + 主流 CMS/博客 + 维基),按优先级命中即用。
    private static final String CONTENT_SELECTORS = String.join(", ",
        "#js_content",                 // 微信公众号
        "[itemprop=articleBody]",
        "article .article-content", ".article-content", ".post-content", ".entry-content",
        ".rich_media_content",         // 微信备用
        ".mw-parser-output",           // 维基
        "article", "main");

    private Element pickContentRoot(Document doc) {
        for (String sel : CONTENT_SELECTORS.split(", ")) {
            Element el = doc.selectFirst(sel);
            if (el != null && !el.text().isBlank()) return el;   // 命名容器是确定性正文,非空即用
        }
        // 启发式:选段落文本量最大的容器
        Element best = null;
        int bestLen = 0;
        for (Element c : doc.select("div, section")) {
            int len = 0;
            for (Element p : c.select("> p")) len += p.text().length();
            if (len > bestLen) { bestLen = len; best = c; }
        }
        if (best != null && bestLen > 200) return best;
        return doc.body();
    }

    private String blocksToText(Element root) {
        if (root == null) return "";
        List<String> blocks = new ArrayList<>();
        for (Element el : root.select("h1, h2, h3, h4, p, blockquote, li, pre")) {
            String t = el.text().trim();
            if (!t.isBlank()) blocks.add(t);
        }
        if (blocks.isEmpty()) {
            String whole = root.wholeText().trim();
            return whole.replaceAll("\\n{3,}", "\n\n");
        }
        return String.join("\n\n", blocks);
    }
}
