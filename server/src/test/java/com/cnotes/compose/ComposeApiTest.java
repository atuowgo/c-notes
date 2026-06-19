package com.cnotes.compose;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import tools.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 创作 API:{@code POST /api/compose}。stub ChatModel 返回固定初稿,断言选中想法被织成非空 draft;
 * 空 noteIds 走友好提示(仍 200,不抛 500)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ComposeApiTest.StubModelConfig.class)
class ComposeApiTest {

    @TestConfiguration
    static class StubModelConfig {
        @Bean @Primary
        ChatModel stubChatModel() {
            return prompt -> new ChatResponse(List.of(new Generation(
                new AssistantMessage("# 论想法的连接\n\n这是由你的想法织成的一篇连贯创作初稿……"))));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired NoteMapper noteMapper;
    @Autowired ObjectMapper om;

    private String seedArticle() {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/compose/" + h); a.setUrlHash(h);
        a.setTitle("创作素材文章"); a.setStatus("done");
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
    void composeFromSeededNotesReturnsNonEmptyDraft() throws Exception {
        String articleId = seedArticle();
        String n1 = seedNote(articleId, "引文一", "想法一");
        String n2 = seedNote(articleId, "引文二", "想法二");

        String body = om.writeValueAsString(Map.of(
            "noteIds", List.of(n1, n2), "topic", "想法的连接"));

        mvc.perform(post("/api/compose").contentType("application/json").content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.draft", not(emptyOrNullString())))
           .andExpect(jsonPath("$.draft", containsString("连贯")));
    }

    @Test
    void composeWithEmptyNoteIdsReturnsGracefulReply() throws Exception {
        String body = om.writeValueAsString(Map.of("noteIds", List.of()));

        mvc.perform(post("/api/compose").contentType("application/json").content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.draft", not(emptyOrNullString())));
    }
}
