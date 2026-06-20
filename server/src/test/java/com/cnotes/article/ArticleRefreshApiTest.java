package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.extract.ContentFetcher;
import com.cnotes.note.dto.CreateNoteRequest;
import com.cnotes.note.dto.NoteAnchor;
import com.cnotes.note.NoteService;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 刷新正文端到端(HTTP):mock 抓取返回「变化后的新正文」,POST /refresh →
 * 后端重定位划线锚点 + 更新正文。断言锚点漂移到新偏移、删去段落的想法被孤立(锚点置空)。
 * mock ContentFetcher / ArticleOrganizer 隔离网络与 DeepSeek。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ArticleRefreshApiTest {

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired NoteService noteService;
    @Autowired NoteMapper noteMapper;
    @MockitoBean ContentFetcher contentFetcher;
    @MockitoBean com.cnotes.organize.ArticleOrganizer organizer;   // 隔离 DeepSeek

    @Test
    void refreshRelocatesAnchorsOnContentChange() throws Exception {
        String oldContent = "自注意力让任意两个位置直接交互。残差连接稳定训练。";
        Article a = new Article();
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        a.setUrl("https://e.com/refresh/" + h); a.setUrlHash(h);
        a.setTitle("刷新测试"); a.setStatus("done"); a.setContent(oldContent);
        articleMapper.insert(a);

        CreateNoteRequest hit = new CreateNoteRequest();
        hit.setArticleId(a.getId()); hit.setQuote("自注意力"); hit.setAnchor(new NoteAnchor(0, 4));
        String hitId = noteService.create(hit).getId();
        CreateNoteRequest miss = new CreateNoteRequest();
        miss.setArticleId(a.getId()); miss.setQuote("残差连接"); miss.setAnchor(new NoteAnchor(12, 16));
        String missId = noteService.create(miss).getId();

        // 抓取返回变化后的正文:前插导言(自注意力右移)、删掉残差那句。
        String newContent = "【导言】Transformer 概述。\n自注意力让任意两个位置直接交互。完。";
        when(contentFetcher.fetch(anyString()))
            .thenReturn(new ContentFetcher.Extracted("刷新测试", newContent));
        // organize 抛错也不应影响正文/锚点更新(优雅降级)。
        when(organizer.organize(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList()))
            .thenThrow(new RuntimeException("LLM 不可用"));

        mvc.perform(post("/api/articles/" + a.getId() + "/refresh"))
           .andExpect(status().isOk());

        // 正文已更新。
        assertThat(articleMapper.selectById(a.getId()).getContent()).isEqualTo(newContent);

        // 命中想法锚点漂移到新偏移,指向 quote。
        Note hitNote = noteMapper.selectById(hitId);
        assertThat(hitNote.getAnchor()).isNotNull();
        assertThat(hitNote.getAnchor()).contains("\"start\":" + newContent.indexOf("自注意力"));

        // 删去段落的想法被孤立:锚点置空。
        assertThat(noteMapper.selectById(missId).getAnchor()).isNull();
    }

    @Test
    void refreshReturns404ForUnknownArticle() throws Exception {
        when(contentFetcher.fetch(anyString()))
            .thenReturn(new ContentFetcher.Extracted("x", "y"));
        mvc.perform(post("/api/articles/" + "0".repeat(32) + "/refresh"))
           .andExpect(status().isNotFound());
    }

    @Test
    void refreshReturns422WhenFetchFails() throws Exception {
        Article a = new Article();
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        a.setUrl("https://e.com/reffail/" + h); a.setUrlHash(h);
        a.setTitle("抓取失败"); a.setStatus("done"); a.setContent("原正文");
        articleMapper.insert(a);

        when(contentFetcher.fetch(anyString())).thenReturn(null);   // 抓取失败

        mvc.perform(post("/api/articles/" + a.getId() + "/refresh"))
           .andExpect(status().isUnprocessableEntity());
    }
}
