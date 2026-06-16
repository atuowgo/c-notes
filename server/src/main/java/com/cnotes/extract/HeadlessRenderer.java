package com.cnotes.extract;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 三级抓取的最重一级:无头浏览器渲染(处理 JS 动态渲染、SPA、强反爬的裸页)。
 *
 * <p>默认<b>关闭</b>(extract.headless.enabled=false):未启用或浏览器二进制缺失时
 * {@link #render} 静默返回空,绝不影响启动与常规 HTTP 抓取。启用需:
 * 配 {@code extract.headless.enabled=true} 且安装 Chromium
 * ({@code ./gradlew ... } 或 {@code npx playwright install chromium})。
 *
 * <p>诚实盲区:看不到用户登录态,可能被反爬拦截。
 */
@Service
public class HeadlessRenderer {

    private static final Logger log = LoggerFactory.getLogger(HeadlessRenderer.class);

    private final boolean enabled;
    private final int timeoutMs;

    public HeadlessRenderer(
            @Value("${extract.headless.enabled:false}") boolean enabled,
            @Value("${extract.headless.timeout-ms:20000}") int timeoutMs) {
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 渲染页面返回完整 DOM HTML;未启用/失败/浏览器缺失均返回空(由上层回退)。 */
    public Optional<String> render(String url) {
        if (!enabled) return Optional.empty();
        // Playwright 非线程安全:每次调用独立创建(兜底路径低频,正确性优先;性能优化留后续)。
        try (Playwright pw = Playwright.create()) {
            try (Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    // 容器/root 下运行 Chromium 必需;低 /dev/shm 环境避免崩溃。
                    .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")))) {
                Page page = browser.newPage();
                // LOAD 比 NETWORKIDLE 稳健(后者在长连接/埋点页易超时);足够拿到渲染后 DOM。
                page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(timeoutMs)
                    .setWaitUntil(WaitUntilState.LOAD));
                return Optional.ofNullable(page.content());
            }
        } catch (Exception e) {
            log.warn("无头渲染失败/不可用 url={} : {}", url, e.toString());
            return Optional.empty();
        }
    }
}
