package com.cnotes.extract;

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

/**
 * 二级抓取(模型清洗 DOM 快照)单测:stub ChatModel 返回清洗后的正文;
 * 断言非空快照被清洗、空快照优雅返回 null。
 */
@SpringBootTest
@Import(DomSnapshotCleanerTest.StubModelConfig.class)
class DomSnapshotCleanerTest {

    @TestConfiguration
    static class StubModelConfig {
        @Bean @Primary
        ChatModel stubChatModel() {
            return prompt -> new ChatResponse(List.of(new Generation(
                new AssistantMessage("清洗后的正文:这是文章主体,导航与广告已去除。"))));
        }
    }

    @Autowired DomSnapshotCleaner cleaner;

    @Test
    void cleansNonBlankSnapshot() {
        String out = cleaner.clean("<html><nav>菜单</nav><article>正文</article><footer>页脚</footer></html>");
        assertThat(out).isNotNull().contains("清洗后的正文");
    }

    @Test
    void blankSnapshotReturnsNull() {
        assertThat(cleaner.clean(null)).isNull();
        assertThat(cleaner.clean("")).isNull();
        assertThat(cleaner.clean("   ")).isNull();
    }
}
