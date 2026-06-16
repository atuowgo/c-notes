package com.cnotes.extract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无头渲染的真实联网验证。默认跳过(需真实 Chromium + 出网),
 * 仅在 HEADLESS_TEST=true 时运行:
 *   PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers HEADLESS_TEST=true \
 *     ./gradlew test --tests "com.cnotes.extract.HeadlessRendererLiveTest"
 */
@EnabledIfEnvironmentVariable(named = "HEADLESS_TEST", matches = "true")
class HeadlessRendererLiveTest {

    @Test
    void rendersRealPage() {
        HeadlessRenderer renderer = new HeadlessRenderer(true, 20_000);
        Optional<String> html = renderer.render("https://example.com/");
        // 结构性校验:无头浏览器成功启动、导航并返回渲染后的 DOM 文档。
        assertThat(html).isPresent();
        assertThat(html.get().toLowerCase()).contains("<html");
        assertThat(html.get().length()).isGreaterThan(200);
    }

    @Test
    void disabledReturnsEmpty() {
        assertThat(new HeadlessRenderer(false, 20_000).render("https://example.com/")).isEmpty();
    }
}
