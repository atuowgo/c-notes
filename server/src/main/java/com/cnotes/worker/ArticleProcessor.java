package com.cnotes.worker;

import com.cnotes.article.ArticleService;
import com.cnotes.article.entity.Article;
import com.cnotes.extract.ContentFetcher;
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

    private final ArticleService articleService;
    private final ArticleOrganizer organizer;
    private final TagClassifier tagClassifier;
    private final ContentFetcher contentFetcher;
    private final ObjectMapper objectMapper;

    @Value("${extract.min-content-length:200}")
    private int minContentLength;

    @Transactional
    public void process(Article a) {
        try {
            articleService.hydrateContent(a);   // 已落盘正文先读回,供 ensureContent/organize
            ensureContent(a);   // 裸 URL(如微信)正文为空时,服务端抓取兜底
            OrganizeResult r = organizer.organize(a.getTitle(), a.getContent(), tagClassifier.allowedTagNames());
            a.setSummary(r.summary());
            a.setKeyPoints(objectMapper.writeValueAsString(r.keyPoints()));
            tagClassifier.apply(a.getId(), r.tags());
            a.setStatus("done");
            a.setProcessedAt(LocalDateTime.now());
            a.setLastError(null);
            articleService.update(a);   // 落盘决策(幂等)+ updateById
        } catch (Exception e) {
            throw new RuntimeException("organize failed", e);  // 退避在 Worker 层(Task 7)
        }
    }

    /** content 为空(微信/裸 URL 提交)时服务端抓取;抓不到则抛错走重试/退避。 */
    private void ensureContent(Article a) {
        if (a.getContent() != null && !a.getContent().isBlank()) return;
        ContentFetcher.Extracted ex = contentFetcher.fetch(a.getUrl());
        if (ex == null || ex.text() == null || ex.text().isBlank() || ex.text().length() < minContentLength) {
            throw new IllegalStateException("content fetch failed or too short: " + a.getUrl());
        }
        a.setContent(ex.text());
        if (ex.html() != null && !ex.html().isBlank()) a.setContentHtml(ex.html());   // 富 HTML 供沉浸式渲染,随 update→offloadHtml 落盘
        a.setExtractMethod("server-fetch");
        if ((a.getTitle() == null || a.getTitle().isBlank()) && ex.title() != null) {
            a.setTitle(ex.title());
        }
    }
}
