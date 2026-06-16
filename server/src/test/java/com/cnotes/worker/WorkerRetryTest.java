package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.organize.ArticleOrganizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class WorkerRetryTest {

    @Autowired ArticleWorker worker;
    @Autowired ArticleMapper articleMapper;
    @MockitoBean ArticleOrganizer organizer;

    @Test
    void failureSetsFailedAndSchedulesBackoff() {
        when(organizer.organize(any(), any(), any())).thenThrow(new RuntimeException("model down"));

        Article a = new Article();
        a.setUrl("https://e.com/f"); a.setUrlHash("000000000000000000000000000000f1");
        a.setContent("已有正文,跳过服务端抓取,聚焦验证 organizer 失败的退避");
        a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        worker.runOne(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("failed");
        assertThat(got.getRetryCount()).isEqualTo(1);
        assertThat(got.getNextRetryTime()).isNotNull();
        assertThat(got.getLastError()).contains("model down");
    }
}
