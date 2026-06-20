package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.extract.ContentFetcher;
import com.cnotes.extract.DomSnapshotCleaner;
import com.cnotes.organize.ArticleOrganizer;
import com.cnotes.organize.OrganizeResult;
import com.cnotes.tag.TagClassifier;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ArticleProcessor {

    private final ArticleMapper articleMapper;
    private final ArticleOrganizer organizer;
    private final TagClassifier tagClassifier;
    private final ContentFetcher contentFetcher;
    private final DomSnapshotCleaner domSnapshotCleaner;
    private final ObjectMapper objectMapper;

    /** 正文短于此长度即视为"提取不佳",逐级兜底(模型清洗快照 → 服务器抓取)。 */
    @Value("${extract.min-content-length:200}")
    private int minContentLength;

    @Transactional
    public void process(Article a) {
        try {
            resolveContent(a);   // 三级抓取:插件正文 → 模型清洗快照 → 服务器抓取
            OrganizeResult r = organizer.organize(a.getTitle(), a.getContent(), tagClassifier.allowedTagNames());
            a.setSummary(r.summary());
            a.setKeyPoints(objectMapper.writeValueAsString(r.keyPoints()));
            tagClassifier.apply(a.getId(), r.tags());
            a.setStatus("done");
            a.setProcessedAt(LocalDateTime.now());
            a.setLastError(null);
            articleMapper.updateById(a);
        } catch (Exception e) {
            throw new RuntimeException("organize failed", e);  // 退避在 Worker 层(Task 7)
        }
    }

    /**
     * 三级抓取兜底(产品设计 §5.4),正文低于阈值才逐级升级、并取最丰富者:
     * <ol>
     *   <li>第 1 级:插件本地 Readability 提取的正文(collect 时带入,够厚就直接用);</li>
     *   <li>第 2 级:正文过薄但有 DOM 快照时,用模型清洗快照抽正文(不发网络);</li>
     *   <li>第 3 级:仍过薄则服务器抓取(HTTP→无头),title 缺失时一并兜底。</li>
     * </ol>
     * 三级都拿不到正文则抛错,交 Worker 退避重试。
     */
    private void resolveContent(Article a) {
        String best = a.getContent();   // 第 1 级:插件正文

        // 第 2 级:正文过薄 + 有快照 → 模型清洗。
        if (len(best) < minContentLength && notBlank(a.getDomSnapshot())) {
            String cleaned = domSnapshotCleaner.clean(a.getDomSnapshot());
            if (len(cleaned) > len(best)) {
                best = cleaned;
                a.setExtractMethod("model-cleaned");
            }
        }

        // 第 3 级:仍过薄 → 服务器抓取(裸 URL / 微信 / 强动态页)。
        if (len(best) < minContentLength) {
            ContentFetcher.Extracted ex = contentFetcher.fetch(a.getUrl());
            if (ex != null && len(ex.text()) > len(best)) {
                best = ex.text();
                a.setExtractMethod("server-fetch");
                if ((a.getTitle() == null || a.getTitle().isBlank()) && ex.title() != null) {
                    a.setTitle(ex.title());
                }
            }
        }

        if (best == null || best.isBlank()) {
            throw new IllegalStateException("content fetch failed: " + a.getUrl());
        }
        a.setContent(best);
    }

    private static int len(String s) { return s == null ? 0 : s.length(); }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
