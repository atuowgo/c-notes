package com.cnotes.organize;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(ArticleOrganizerTest.StubModelConfig.class)
class ArticleOrganizerTest {

    @TestConfiguration
    static class StubModelConfig {
        @Bean @Primary
        ChatModel stubChatModel() {
            // 返回与 OrganizeResult 字段一致的 JSON;.entity() 负责解析
            return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"summary\":\"摘要X\",\"keyPoints\":[\"要点1\",\"要点2\"],\"tags\":[\"Rust\",\"新标签\"]}"))));
        }
    }

    @Autowired ArticleOrganizer organizer;

    @Test
    void parsesStructuredOutput() {
        OrganizeResult r = organizer.organize("标题", "正文", List.of("Rust"));
        assertThat(r.summary()).isEqualTo("摘要X");
        assertThat(r.keyPoints()).containsExactly("要点1", "要点2");
        assertThat(r.tags()).contains("Rust", "新标签");
    }
}
