package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.storage.StorageService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleServiceHtmlTest {
    private final ArticleMapper mapper = mock(ArticleMapper.class);
    private final StorageService storage = mock(StorageService.class);
    private final ArticleService svc = new ArticleService(mapper, storage, 20000);

    @Test void offloadHtmlWritesStorageAndSetsKey() {
        Article a = new Article();
        a.setContentHtml("<p>正文HTML</p>");
        svc.offloadHtml(a);
        verify(storage).put(anyString(), eq("<p>正文HTML</p>"));
        assertThat(a.getHtmlObjectKey()).isNotBlank();
        assertThat(a.getContentHtml()).isNull();   // 落盘后清瞬态
    }

    @Test void offloadHtmlIdempotentWhenKeyExists() {
        Article a = new Article();
        a.setHtmlObjectKey("existing");
        a.setContentHtml("<p>不该再写</p>");
        svc.offloadHtml(a);
        verify(storage, never()).put(anyString(), anyString());
        assertThat(a.getHtmlObjectKey()).isEqualTo("existing");
        assertThat(a.getContentHtml()).isNull();   // 仅清瞬态
    }

    @Test void offloadHtmlNoopWhenBlank() {
        Article a = new Article();
        a.setContentHtml("  ");
        svc.offloadHtml(a);
        verify(storage, never()).put(anyString(), anyString());
        assertThat(a.getHtmlObjectKey()).isNull();
    }

    @Test void hydrateHtmlReadsBackByKey() {
        Article a = new Article();
        a.setHtmlObjectKey("k1");
        when(storage.get("k1")).thenReturn("<p>回读</p>");
        svc.hydrateHtml(a);
        assertThat(a.getContentHtml()).isEqualTo("<p>回读</p>");
    }
}
