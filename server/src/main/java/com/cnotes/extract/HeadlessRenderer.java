package com.cnotes.extract;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 三级抓取的最重一级:无头浏览器渲染(处理 JS 动态渲染、SPA、强反爬的裸页)。
 *
 * <p>默认<b>关闭</b>(extract.headless.enabled=false):未启用或浏览器二进制缺失时
 * {@link #render} 静默返回空,绝不影响启动与常规 HTTP 抓取。
 *
 * <p><b>实例池化</b>:Playwright/Browser <b>非线程安全且要求与创建线程同线程使用</b>,
 * 故每个 {@link RenderWorker} 持有一条专属单线程 + 在该线程上惰性创建并<b>长期复用</b>的
 * Playwright+Browser;渲染只在该线程上开一个全新 {@link BrowserContext}(隔离 cookie/状态)、
 * 用完即关。池容量 {@code extract.headless.pool-size}(默认 2)条 worker,借出-归还、阻塞等待空闲。
 * 相比"每次新建 Playwright+Browser",省掉了最贵的浏览器启动开销,同时不违反线程模型。
 *
 * <p>诚实盲区:看不到用户登录态,可能被反爬拦截。
 */
@Service
public class HeadlessRenderer {

    private static final Logger log = LoggerFactory.getLogger(HeadlessRenderer.class);

    private final boolean enabled;
    private final int timeoutMs;
    private final int poolSize;

    /** 空闲 worker 队列;惰性填充(首次启用渲染时建池)。 */
    private volatile BlockingQueue<RenderWorker> pool;

    public HeadlessRenderer(
            @Value("${extract.headless.enabled:false}") boolean enabled,
            @Value("${extract.headless.timeout-ms:20000}") int timeoutMs,
            @Value("${extract.headless.pool-size:2}") int poolSize) {
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.poolSize = Math.max(1, poolSize);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 渲染页面返回完整 DOM HTML;未启用/失败/浏览器缺失/无空闲且超时均返回空(由上层回退)。 */
    public Optional<String> render(String url) {
        if (!enabled) return Optional.empty();
        BlockingQueue<RenderWorker> p = ensurePool();

        RenderWorker worker;
        try {
            // 等待一个空闲 worker;给足"借出"等待(渲染本身的超时在 worker 内部控制)。
            worker = p.poll(timeoutMs + 5_000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (worker == null) {
            log.warn("无头渲染池繁忙,放弃 url={}", url);
            return Optional.empty();
        }
        try {
            return worker.render(url, timeoutMs);
        } catch (Exception e) {
            log.warn("无头渲染失败/不可用 url={} : {}", url, e.toString());
            return Optional.empty();
        } finally {
            p.offer(worker);   // 归还(worker 内部已重置上下文,Browser 复用)
        }
    }

    private BlockingQueue<RenderWorker> ensurePool() {
        BlockingQueue<RenderWorker> p = pool;
        if (p == null) {
            synchronized (this) {
                p = pool;
                if (p == null) {
                    p = new LinkedBlockingQueue<>();
                    for (int i = 0; i < poolSize; i++) p.offer(new RenderWorker(i));
                    pool = p;
                    log.info("无头渲染池已建,容量={}", poolSize);
                }
            }
        }
        return p;
    }

    @PreDestroy
    public void shutdown() {
        BlockingQueue<RenderWorker> p = pool;
        if (p == null) return;
        for (RenderWorker w : p) w.close();
        p.clear();
    }

    /**
     * 单个池成员:一条专属单线程 + 在该线程上复用的 Playwright/Browser。
     * 所有 Playwright 调用都 submit 到这条线程执行,严格满足"同创建线程使用"。
     */
    private static final class RenderWorker {
        private final ExecutorService exec;
        private Playwright pw;       // 仅在 exec 线程访问
        private Browser browser;     // 仅在 exec 线程访问

        RenderWorker(int idx) {
            this.exec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "headless-renderer-" + idx);
                t.setDaemon(true);
                return t;
            });
        }

        Optional<String> render(String url, int timeoutMs) throws Exception {
            Future<Optional<String>> f = exec.submit(() -> {
                ensureBrowser();
                try (BrowserContext ctx = browser.newContext()) {   // 每次全新上下文,状态隔离
                    Page page = ctx.newPage();
                    page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(timeoutMs)
                        .setWaitUntil(WaitUntilState.LOAD));
                    return Optional.ofNullable(page.content());
                }
            });
            // 给渲染留出超时余量;超时则取消任务(上层回退)。
            try {
                return f.get(timeoutMs + 10_000L, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                f.cancel(true);
                throw e;
            }
        }

        /** 在 exec 线程上惰性创建/重建 Browser(断连后自愈)。 */
        private void ensureBrowser() {
            if (pw == null) {
                pw = Playwright.create();
            }
            if (browser == null || !browser.isConnected()) {
                if (browser != null) {
                    try { browser.close(); } catch (Exception ignore) { /* 重建即可 */ }
                }
                browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    // 容器/root 下运行 Chromium 必需;低 /dev/shm 环境避免崩溃。
                    .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")));
            }
        }

        void close() {
            try {
                exec.submit(() -> {
                    try { if (browser != null) browser.close(); } catch (Exception ignore) { }
                    try { if (pw != null) pw.close(); } catch (Exception ignore) { }
                }).get(15, TimeUnit.SECONDS);
            } catch (Exception ignore) {
                // 关闭尽力而为
            } finally {
                exec.shutdownNow();
            }
        }
    }
}
