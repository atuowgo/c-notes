package com.cnotes.extract;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {
    private final HtmlSanitizer s = new HtmlSanitizer();

    @Test void stripsScriptAndEventHandlers() {
        String out = s.sanitize("<p onclick=\"x()\">hi</p><script>evil()</script>", "https://e.com");
        assertThat(out).contains("hi").doesNotContain("script").doesNotContain("onclick");
    }

    @Test void keepsStructuralTagsAndImages() {
        String html = "<h2>标题</h2><p>段落</p><ul><li>项</li></ul>"
            + "<blockquote>引</blockquote><pre><code>code</code></pre>"
            + "<img src=\"https://e.com/a.png\" alt=\"图\">";
        String out = s.sanitize(html, "https://e.com");
        assertThat(out).contains("<h2").contains("<ul").contains("<blockquote")
            .contains("<pre").contains("<img").contains("a.png");
    }

    @Test void normalizesLazyDataSrcToSrc() {
        String out = s.sanitize("<img data-src=\"https://e.com/lazy.png\">", "https://e.com");
        assertThat(out).contains("src=\"https://e.com/lazy.png\"").doesNotContain("data-src");
    }

    @Test void dropsNonHttpsImage() {
        String out = s.sanitize("<img src=\"http://e.com/x.png\"><img src=\"https://e.com/y.png\">", "https://e.com");
        assertThat(out).doesNotContain("x.png").contains("y.png");
    }

    @Test void nullOrBlankReturnsEmpty() {
        assertThat(s.sanitize(null, "https://e.com")).isEmpty();
        assertThat(s.sanitize("", "https://e.com")).isEmpty();
    }

    @Test void srcNonBlankNotOverriddenByDataSrc() {
        String out = s.sanitize("<img src=\"https://e.com/real.png\" data-src=\"https://e.com/lazy.png\">", "https://e.com");
        assertThat(out).contains("real.png").doesNotContain("lazy.png").doesNotContain("data-src");
    }

    @Test void emptyDataSrcDropsImgNotFakeBaseUri() {   // #1 回归:空 data-src 不能洗白成 baseUri 假图
        String out = s.sanitize("<img data-src=\"\">", "https://e.com");
        assertThat(out).doesNotContain("<img");
    }

    @Test void httpImgFullyRemovedNotNakedTag() {   // #2:http 图整体移除,不留裸 <img>
        String out = s.sanitize("<img src=\"http://e.com/x.png\">正文", "https://e.com");
        assertThat(out).doesNotContain("<img").contains("正文");
    }
}
