package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.extract.ContentFetcher;
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
    @MockitoBean ContentFetcher contentFetcher;

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

    @Test
    void blankContentTriggersServerFetchThenOrganizes() {
        String fetched = "服务端抓到的正文内容".repeat(20);   // 需 >= min-content-length(200),否则视为抓取过薄
        when(contentFetcher.fetch(any()))
            .thenReturn(new ContentFetcher.Extracted("抓到的标题", fetched, "<p>" + fetched + "</p>"));
        when(organizer.organize(any(), any(), any()))
            .thenReturn(new OrganizeResult("摘要", List.of("要点1"), List.of()));

        // 模拟微信入库:仅 URL,无 content、无 title
        Article a = new Article();
        a.setUrl("https://mp.weixin.qq.com/s/demo"); a.setUrlHash("000000000000000000000000000000fe");
        a.setStatus("processing"); a.setRetryCount(0); a.setSourceType("wechat");
        articleMapper.insert(a);

        processor.process(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("done");
        assertThat(got.getContent()).isEqualTo(fetched);
        assertThat(got.getExtractMethod()).isEqualTo("server-fetch");
        assertThat(got.getTitle()).isEqualTo("抓到的标题");   // 标题回填
    }
}
