package com.cnotes.collect;

import com.cnotes.article.ArticleService;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.collect.dto.CollectRequest;
import com.cnotes.extract.HtmlSanitizer;
import com.cnotes.user.CurrentUserResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CollectServiceTest {
    private final ArticleMapper mapper = mock(ArticleMapper.class);
    private final ArticleService articleService = mock(ArticleService.class);
    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    private final HtmlSanitizer sanitizer = new HtmlSanitizer();   // 用真净化器,验证确被调用
    // 注意:构造实参顺序需与 CollectService 字段声明顺序一致
    private final CollectService svc = new CollectService(mapper, articleService, currentUser, sanitizer);

    private CollectRequest req(String url, String html) {
        CollectRequest r = new CollectRequest();
        r.setUrl(url);
        r.setContentHtml(html);
        return r;
    }

    @Test void sanitizesContentHtmlOntoArticle() {
        when(mapper.selectOne(any())).thenReturn(null);   // 无重复,走新增分支
        svc.collect(req("https://e.com/a", "<p>正文</p><script>evil()</script>"));
        ArgumentCaptor<Article> cap = ArgumentCaptor.forClass(Article.class);
        verify(articleService).save(cap.capture());
        assertThat(cap.getValue().getContentHtml()).contains("正文").doesNotContain("script");
    }

    @Test void nullContentHtmlStaysNull() {
        when(mapper.selectOne(any())).thenReturn(null);
        svc.collect(req("https://e.com/a", null));
        ArgumentCaptor<Article> cap = ArgumentCaptor.forClass(Article.class);
        verify(articleService).save(cap.capture());
        assertThat(cap.getValue().getContentHtml()).isNull();
    }
}
