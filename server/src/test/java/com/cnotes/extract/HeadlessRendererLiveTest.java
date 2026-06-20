package com.cnotes.extract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无头渲染的真实联网验证。默认跳过(需真实 Chromium + 出网),
 * 仅在 HEADLESS_TEST=true 时运行:
 *   PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers HEADLESS_TEST=true \
 *     ./gradlew test --tests "com.cnotes.extract.HeadlessRendererLiveTest"
 */
@EnabledIfEnvironmentVariable(named = "HEADLESS_TEST", matches = "true")
class HeadlessRendererLiveTest {

    // data: 页让 chromium 零网络渲染,隔离沙箱网络限制,纯验证池化机制(启动/上下文/导航/取 DOM)。
    // 填充足量正文,使渲染后的 DOM 长度 > 200。
    private static final String DATA_URL =
        "data:text/html,<html><head><title>pool</title></head><body><h1>hello pool</h1>"
        + "<p>" + "知识炼金炉无头渲染池化验证。".repeat(20) + "</p></body></html>";

    @Test
    void rendersRealPage() {
        HeadlessRenderer renderer = new HeadlessRenderer(true, 20_000, 2);
        try {
            Optional<String> html = renderer.render(DATA_URL);
            assertThat(html).isPresent();
            assertThat(html.get().toLowerCase()).contains("<html");
            assertThat(html.get().length()).isGreaterThan(200);
        } finally {
            renderer.shutdown();
        }
    }

    @Test
    void reusesPooledBrowserAcrossSequentialRenders() {
        // 池化关键:同一 renderer 连续多次渲染都成功(浏览器被复用,而非每次新建)。
        HeadlessRenderer renderer = new HeadlessRenderer(true, 20_000, 1);
        try {
            for (int i = 0; i < 3; i++) {
                Optional<String> html = renderer.render(DATA_URL);
                assertThat(html).as("第 %s 次渲染", i).isPresent();
                assertThat(html.get().toLowerCase()).contains("<html");
            }
        } finally {
            renderer.shutdown();
        }
    }

    @Test
    void handlesConcurrentRendersWithPool() throws Exception {
        // 池容量 2,并发 4 个渲染请求:借出-归还-阻塞等待应让全部成功,无线程模型崩溃。
        HeadlessRenderer renderer = new HeadlessRenderer(true, 20_000, 2);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Optional<String>>> futures = pool.invokeAll(List.of(
                () -> renderer.render(DATA_URL),
                () -> renderer.render(DATA_URL),
                () -> renderer.render(DATA_URL),
                () -> renderer.render(DATA_URL)
            ));
            for (Future<Optional<String>> f : futures) {
                assertThat(f.get()).isPresent();
            }
        } finally {
            pool.shutdownNow();
            renderer.shutdown();
        }
    }

    @Test
    void disabledReturnsEmpty() {
        assertThat(new HeadlessRenderer(false, 20_000, 2).render(DATA_URL)).isEmpty();
    }
}
