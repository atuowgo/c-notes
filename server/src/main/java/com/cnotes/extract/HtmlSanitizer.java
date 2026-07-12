package com.cnotes.extract;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 正文 HTML 净化:保留语义结构标签+图片,剥离脚本/样式/事件处理器/非 https 图。
 * data-src 懒加载归一化到 src;被剥掉 src 的裸 img(http 图/空懒加载)整体移除,避免裂图。
 * 失败(畸形/空)返回空串,由上层降级,绝不抛错阻断入库。
 */
@Service
public class HtmlSanitizer {

    private static final Logger log = LoggerFactory.getLogger(HtmlSanitizer.class);

    // relaxed() 默认已含 img 的 src/alt 及 http+https 协议;这里补 figure/figcaption,并去掉 http 只留 https 图。
    private static final Safelist SAFELIST = Safelist.relaxed()
        .addTags("figure", "figcaption")
        .removeProtocols("img", "src", "http");

    /** data-src→src 懒加载归一化后按 Safelist 净化;baseUri 用于解析相对链接。 */
    public String sanitize(String html, String baseUri) {
        if (html == null || html.isBlank()) return "";
        String base = baseUri == null ? "" : baseUri;
        try {
            Document dirty = Jsoup.parse(html, base);
            dirty.select("img[data-src]").forEach(img -> {
                String lazy = img.attr("data-src");
                if (!lazy.isBlank() && img.attr("src").isBlank()) img.attr("src", lazy);
                img.removeAttr("data-src");
            });
            Document clean = new Cleaner(SAFELIST).clean(dirty);
            clean.select("img:not([src])").remove();   // 剥掉无 src 的裸 img(http 图被剥/空 data-src)
            return clean.body().html();
        } catch (Exception e) {
            log.warn("HTML 净化失败,降级为空; baseUri={}", base, e);
            return "";
        }
    }
}
