package com.cnotes.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.chat.entity.ChatMessage;
import com.cnotes.chat.entity.ChatSession;
import com.cnotes.chat.mapper.ChatMessageMapper;
import com.cnotes.chat.mapper.ChatSessionMapper;
import com.cnotes.chat.vector.KnowledgeRetriever;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P6:深聊 API。{@code POST /api/articles/{id}/chat {message}} → 200,响应含非空 reply + sessionId,
 * 并在 H2 落 1 个 session + 2 条 message(user+assistant),assistant 行带 sources。
 * ChatModel 用 stub bean(@Primary)返回固定串,断言持久化与 sources 装配;源2 检索用
 * @MockitoBean 隔离 Ark 网络(其自身在 KnowledgeRetrieverTest 验证)。本机真实 LLM 由 P9 门控覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ChatApiTest.StubModelConfig.class)
class ChatApiTest {

    @TestConfiguration
    static class StubModelConfig {
        @Bean @Primary
        ChatModel stubChatModel() {
            return prompt -> new ChatResponse(List.of(new Generation(
                new AssistantMessage("红烧牛肉要先焯水去腥,再小火慢炖入味。"))));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ArticleMapper articleMapper;
    @Autowired ChatSessionMapper sessionMapper;
    @Autowired ChatMessageMapper messageMapper;
    @MockitoBean KnowledgeRetriever knowledgeRetriever;

    private String seedArticle() {
        String h = java.util.UUID.randomUUID().toString().replace("-", "");
        Article a = new Article();
        a.setUrl("https://e.com/chat/" + h); a.setUrlHash(h);
        a.setTitle("红烧牛肉教程"); a.setSummary("讲红烧牛肉的家常做法");
        a.setContent("正文……"); a.setKeyPoints("[\"焯水去腥\",\"小火慢炖\"]");
        a.setStatus("done");
        articleMapper.insert(a);
        return a.getId();
    }

    @Test
    void chatPersistsSessionAndTwoMessagesWithReply() throws Exception {
        when(knowledgeRetriever.retrieve(anyString(), anyInt(), anyString()))
            .thenReturn(List.of(new KnowledgeRetriever.Hit("cook", "烹饪", "炖肉技巧综述", 0.9)));
        String articleId = seedArticle();

        MvcResult res = mvc.perform(post("/api/articles/" + articleId + "/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"怎么做更入味\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
            .andExpect(jsonPath("$.reply", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
            .andExpect(jsonPath("$.sources", org.hamcrest.Matchers.hasItem("📄")))
            .andExpect(jsonPath("$.sources", org.hamcrest.Matchers.hasItem("🕸")))
            .andReturn();

        String sessionId = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.sessionId");

        ChatSession session = sessionMapper.selectById(sessionId);
        assertThat(session).isNotNull();
        assertThat(session.getArticleId()).isEqualTo(articleId);

        List<ChatMessage> msgs = messageMapper.selectList(
            Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime, ChatMessage::getRole));
        assertThat(msgs).hasSize(2);
        ChatMessage user = msgs.stream().filter(m -> "user".equals(m.getRole())).findFirst().orElseThrow();
        ChatMessage assistant = msgs.stream().filter(m -> "assistant".equals(m.getRole())).findFirst().orElseThrow();
        assertThat(user.getContent()).isEqualTo("怎么做更入味");
        assertThat(assistant.getContent()).contains("小火慢炖");
        assertThat(assistant.getSources()).contains("📄").contains("🕸");
    }

    @Test
    void reusesExistingSessionWhenSessionIdProvided() throws Exception {
        when(knowledgeRetriever.retrieve(anyString(), anyInt(), anyString())).thenReturn(List.of());
        String articleId = seedArticle();

        MvcResult first = mvc.perform(post("/api/articles/" + articleId + "/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"第一问\"}"))
            .andExpect(status().isOk()).andReturn();
        String sessionId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.sessionId");

        mvc.perform(post("/api/articles/" + articleId + "/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"第二问\",\"sessionId\":\"" + sessionId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId", org.hamcrest.Matchers.is(sessionId)));

        assertThat(messageMapper.selectList(
            Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getSessionId, sessionId))).hasSize(4);
        assertThat(sessionMapper.selectCount(
            Wrappers.<ChatSession>lambdaQuery().eq(ChatSession::getArticleId, articleId))).isEqualTo(1L);
    }
}
