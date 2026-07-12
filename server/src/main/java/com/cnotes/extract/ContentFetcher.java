package com.cnotes.extract;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务端正文抓取(三级抓取的服务器侧:处理裸 URL、微信公众号文章)。
 *
 * <p>抓取分级:① 一次 HTTP 抓取(jsoup)+ 启发式提取——微信公众号等服务端渲染页够用;
 * ② HTTP 结果过薄(强动态/SPA 页)且开启时,用 {@link HeadlessRenderer} 无头渲染后再提取,
 * 取更丰富者。
 */
@Service
public class ContentFetcher {

    public record Extracted(String title, String text, String html) {}

    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BODY = 5 * 1024 * 1024;
    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final HeadlessRenderer headlessRenderer;
    private final int minContentLength;
    private final HtmlSanitizer sanitizer;

    public ContentFetcher(HeadlessRenderer headlessRenderer,
                          @Value("${extract.min-content-length:200}") int minContentLength,
                          HtmlSanitizer sanitizer) {
        this.headlessRenderer = headlessRenderer;
        this.minContentLength = minContentLength;
        this.sanitizer = sanitizer;
    }

    // 站点明确拒绝(非网络抖动),重试无意义:401 未授权、403 反爬拒绝、404 页面不存在、451 依法下线。
    private static final Set<Integer> BLOCKING_STATUS = Set.of(401, 403, 404, 451);

    // 微信"环境异常/滑块验证"跳转页(如 /mp/wappoc_appmsgcaptcha):抓到的是验证页壳,不是文章正文;
    // 真实文章 URL 编码放在 target_url 参数里,直接解出来抓才对——验证页本身无法用无头浏览器绕过。
    private static final Pattern WECHAT_CAPTCHA_TARGET =
        Pattern.compile("^https?://mp\\.weixin\\.qq\\.com/mp/[^?]*captcha[^?]*\\?.*[?&]target_url=([^&]+)",
            Pattern.CASE_INSENSITIVE);

    static String unwrapWechatCaptcha(String url) {
        if (url == null) return null;
        Matcher m = WECHAT_CAPTCHA_TARGET.matcher(url);
        if (!m.find()) return url;
        return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
    }

    /**
     * 抓取并提取。网络抖动/解析失败等瞬时问题返回 null,由上层重试;
     * 站点明确拒绝(见 {@link #BLOCKING_STATUS})且无头兜底也拿不到内容时抛 {@link ContentFetchBlockedException},
     * 上层应据此放弃重试,而不是当瞬时失败退避。
     */
    public Extracted fetch(String rawUrl) {
        String url = unwrapWechatCaptcha(rawUrl);
        Integer[] blockedStatus = new Integer[1];
        Extracted httpResult = httpFetch(url, blockedStatus);
        Extracted result = resolve(url, httpResult);
        if (textLen(result) < minContentLength && blockedStatus[0] != null) {
            throw new ContentFetchBlockedException(url, blockedStatus[0]);
        }
        return result;
    }

    /**
     * 编排:HTTP 提取结果够厚就用;过薄(或失败)且无头可用时渲染后再提取,取更丰富者。
     * 包级可见以便单测,不发网络。
     */
    Extracted resolve(String url, Extracted httpResult) {
        if (textLen(httpResult) >= minContentLength) return httpResult;
        Optional<String> rendered = headlessRenderer.render(url);
        if (rendered.isPresent()) {
            Extracted headless = extractHtml(url, rendered.get());
            if (textLen(headless) > textLen(httpResult)) return headless;
        }
        return httpResult;
    }

    private static int textLen(Extracted e) {
        return e == null || e.text() == null ? 0 : e.text().length();
    }

    /** blockedStatus[0] 回填站点明确拒绝时的 HTTP 状态码(其余失败场景保持 null,视为瞬时问题)。 */
    private Extracted httpFetch(String url, Integer[] blockedStatus) {
        try {
            Document doc = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_BODY)
                .followRedirects(true)
                .get();
            return extract(doc);
        } catch (HttpStatusException e) {
            if (BLOCKING_STATUS.contains(e.getStatusCode())) blockedStatus[0] = e.getStatusCode();
            return null;
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
        String html = root == null ? "" : sanitizer.sanitize(root.html(), doc.baseUri());
        return new Extracted(title, text, html);
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
