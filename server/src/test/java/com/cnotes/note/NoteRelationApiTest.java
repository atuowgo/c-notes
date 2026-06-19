package com.cnotes.note;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 批注关联 API:{@code GET /api/notes/{id}/related}。stub ChatModel 返回纯文本(无法解析为结构化挑选),
 * 触发"同篇"降级路径;首次计算并落库,二次调用直接读已存关联(均 200 数组)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(NoteRelationApiTest.StubModelConfig.class)
class NoteRelationApiTest {

    @TestConfiguration
    static class StubModelConfig {
        @Bean @Primary
        ChatModel stubChatModel() {
            return prompt -> new ChatResponse(List.of(new Generation(
                new AssistantMessage("这是一段无法被解析为结构化关联的纯文本回复。"))));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired NoteMapper noteMapper;

    private String seedArticle(String title) {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/rel/" + h); a.setUrlHash(h);
        a.setTitle(title); a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    private String seedNote(String articleId, String quote, String thought) {
        Note n = new Note();
        n.setArticleId(articleId);
        n.setQuote(quote);
        n.setThought(thought);
        noteMapper.insert(n);
        return n.getId();
    }

    @Test
    void relatedFallsBackToSameArticleAndPersistsForReuse() throws Exception {
        String articleId = seedArticle("关联测试文章");
        String n1 = seedNote(articleId, "引文一", "想法一");
        seedNote(articleId, "引文二", "想法二");
        seedNote(articleId, "引文三", "想法三");

        // 首次:LLM 纯文本无法解析 -> 降级"同篇",落库
        mvc.perform(get("/api/notes/" + n1 + "/related"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(greaterThan(0))))
           .andExpect(jsonPath("$[0].relationType", is("同篇")))
           .andExpect(jsonPath("$[0].reason", is("同一篇文章的其它想法")))
           .andExpect(jsonPath("$[0].note.id", not(emptyOrNullString())));

        // 二次:直接读已存关联,结果一致
        mvc.perform(get("/api/notes/" + n1 + "/related"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(greaterThan(0))))
           .andExpect(jsonPath("$[0].relationType", is("同篇")));
    }

    @Test
    void relatedForUnknownNoteReturnsEmptyArray() throws Exception {
        mvc.perform(get("/api/notes/" + "0".repeat(32) + "/related"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", hasSize(0)));
    }
}
