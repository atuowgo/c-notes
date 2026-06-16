package com.cnotes.cluster;

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
@Import(ClusterSummarizerTest.StubModelConfig.class)
class ClusterSummarizerTest {

    @TestConfiguration
    static class StubModelConfig {
        @Bean @Primary
        ChatModel stubChatModel() {
            return prompt -> new ChatResponse(List.of(new Generation(
                new AssistantMessage("这是织好的演进式综述:该主题围绕 X 展开……"))));
        }
    }

    @Autowired ClusterSummarizer summarizer;

    @Test
    void weavesArticlesIntoSummary() {
        String s = summarizer.summarize("LLM 推理优化", List.of(
            new ClusterSummarizer.ArticleBrief("文章一", "摘要一", List.of("要点A")),
            new ClusterSummarizer.ArticleBrief("文章二", "摘要二", List.of("要点B"))));
        assertThat(s).contains("演进式综述");
    }
}
