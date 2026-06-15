package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.organize.ArticleOrganizer;
import com.cnotes.organize.OrganizeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ArticleProcessorTest {

    @Autowired ArticleProcessor processor;
    @Autowired ArticleMapper articleMapper;
    @MockitoBean ArticleOrganizer organizer;

    @Test
    void processFillsSummaryPointsAndMarksDone() {
        when(organizer.organize(any(), any(), any()))
            .thenReturn(new OrganizeResult("摘要", List.of("要点1", "要点2"), List.of("Rust", "新标签")));

        Article a = new Article();
        a.setUrl("https://e.com/p"); a.setUrlHash("00000000000000000000000000000099");
        a.setTitle("标题"); a.setContent("正文"); a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        processor.process(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("done");
        assertThat(got.getSummary()).isEqualTo("摘要");
        assertThat(got.getKeyPoints()).contains("要点1");
        assertThat(got.getProcessedAt()).isNotNull();
    }
}
