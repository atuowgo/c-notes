package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.extract.ContentFetchBlockedException;
import com.cnotes.extract.ContentFetcher;
import com.cnotes.organize.ArticleOrganizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ArticleWorkerTest {

    @Autowired ArticleWorker worker;
    @Autowired ArticleMapper articleMapper;
    @MockitoBean ArticleOrganizer organizer;
    @MockitoBean ContentFetcher contentFetcher;

    @Test
    void blockedFetchFailsWithoutSchedulingRetry() {
        when(contentFetcher.fetch(any())).thenThrow(new ContentFetchBlockedException("https://e.com/x", 403));

        Article a = new Article();
        a.setUrl("https://e.com/x"); a.setUrlHash("0000000000000000000000000000aaa1");
        a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        worker.runOne(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("failed");
        assertThat(got.getRetryCount()).isEqualTo(1);
        assertThat(got.getNextRetryTime()).isNull();   // 站点永久拒绝,不排队重试
        assertThat(got.getLastError()).contains("403");
    }

    @Test
    void transientFailureSchedulesBackoffRetry() {
        when(contentFetcher.fetch(any())).thenThrow(new RuntimeException("timeout"));

        Article a = new Article();
        a.setUrl("https://e.com/y"); a.setUrlHash("0000000000000000000000000000aaa2");
        a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        worker.runOne(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("failed");
        assertThat(got.getNextRetryTime()).isNotNull();   // 瞬时失败,按退避排队重试
    }
}
