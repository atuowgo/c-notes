package com.cnotes.worker;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.extract.ContentFetcher;
import com.cnotes.extract.DomSnapshotCleaner;
import com.cnotes.organize.ArticleOrganizer;
import com.cnotes.organize.OrganizeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ArticleProcessorTest {

    @Autowired ArticleProcessor processor;
    @Autowired ArticleMapper articleMapper;
    @MockitoBean ArticleOrganizer organizer;
    @MockitoBean ContentFetcher contentFetcher;
    @MockitoBean DomSnapshotCleaner domSnapshotCleaner;

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
    void thinContentWithSnapshotUsesModelCleanedLevel2NotServerFetch() {
        // 第 2 级:插件正文过薄但带 DOM 快照 → 模型清洗快照得正文,且不应再走第 3 级服务器抓取。
        String cleaned = "模型从快照里清洗出的正文主体,足够长足够长足够长足够长足够长足够长足够长足够长足够长足够长"
            .repeat(8);
        when(domSnapshotCleaner.clean(any())).thenReturn(cleaned);
        when(organizer.organize(any(), any(), any()))
            .thenReturn(new OrganizeResult("摘要", List.of("要点1"), List.of()));

        Article a = new Article();
        a.setUrl("https://e.com/spa"); a.setUrlHash("000000000000000000000000000000a2");
        a.setTitle("SPA 页"); a.setContent("太短");   // < min-content-length
        a.setDomSnapshot("<html><nav>导航广告</nav><article>真正正文……</article></html>");
        a.setStatus("processing"); a.setRetryCount(0);
        articleMapper.insert(a);

        processor.process(a);

        Article got = articleMapper.selectById(a.getId());
        assertThat(got.getStatus()).isEqualTo("done");
        assertThat(got.getContent()).isEqualTo(cleaned);
        assertThat(got.getExtractMethod()).isEqualTo("model-cleaned");
        // 第 2 级已拿到足够正文 → 不再触发第 3 级服务器抓取。
        verify(contentFetcher, never()).fetch(any());
    }

    @Test
    void blankContentTriggersServerFetchThenOrganizes() {
        when(contentFetcher.fetch(any()))
            .thenReturn(new ContentFetcher.Extracted("抓到的标题", "服务端抓到的正文内容"));
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
        assertThat(got.getContent()).isEqualTo("服务端抓到的正文内容");
        assertThat(got.getExtractMethod()).isEqualTo("server-fetch");
        assertThat(got.getTitle()).isEqualTo("抓到的标题");   // 标题回填
    }
}
