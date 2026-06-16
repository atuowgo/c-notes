package com.cnotes.chat;

import com.cnotes.chat.entity.ChatMessage;
import com.cnotes.chat.entity.ChatSession;
import com.cnotes.chat.mapper.ChatMessageMapper;
import com.cnotes.chat.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChatPersistenceTest {

    @Autowired
    ChatSessionMapper sessionMapper;
    @Autowired
    ChatMessageMapper messageMapper;

    @Test
    void persistsSessionAndMessages() {
        ChatSession session = new ChatSession();
        session.setArticleId(null); // articleId 可空
        session.setTitle("深聊一篇文章");
        sessionMapper.insert(session);

        assertThat(session.getId()).hasSize(32);
        ChatSession got = sessionMapper.selectById(session.getId());
        assertThat(got.getCreateTime()).isNotNull();

        ChatMessage user = new ChatMessage();
        user.setSessionId(session.getId());
        user.setRole("user");
        user.setContent("这篇文章的核心观点是什么？");
        messageMapper.insert(user);

        ChatMessage assistant = new ChatMessage();
        assistant.setSessionId(session.getId());
        assistant.setRole("assistant");
        assistant.setContent("核心观点是……");
        assistant.setSources("[{\"type\":\"article\"},{\"type\":\"web\"}]"); // sources JSON 文本
        messageMapper.insert(assistant);

        assertThat(user.getId()).hasSize(32);
        assertThat(assistant.getCreateTime()).isNotNull();

        List<ChatMessage> messages = messageMapper.selectList(
                new QueryWrapper<ChatMessage>().eq("session_id", session.getId()));
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(ChatMessage::getRole)
                .containsExactlyInAnyOrder("user", "assistant");
        ChatMessage savedAssistant = messages.stream()
                .filter(m -> "assistant".equals(m.getRole())).findFirst().orElseThrow();
        assertThat(savedAssistant.getSources()).contains("web");
        assertThat(savedAssistant.getCreateTime()).isNotNull();
    }
}
