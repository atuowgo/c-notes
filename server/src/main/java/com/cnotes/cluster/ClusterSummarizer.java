package com.cnotes.cluster;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 演进式综述生成(Karpathy LLM-Wiki 思路):把同一主题簇下的多篇文章织成一篇
 * 有逻辑、连贯的概览——不是罗列,而是呈现脉络、共识与分歧。
 */
@Service
public class ClusterSummarizer {

    private final ChatClient chatClient;

    public ClusterSummarizer(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public record ArticleBrief(String title, String summary, List<String> keyPoints) {}

    public String summarize(String topic, List<ArticleBrief> articles) {
        String body = articles.stream()
            .map(a -> "- 《" + nz(a.title()) + "》摘要:" + nz(a.summary())
                + (a.keyPoints() == null || a.keyPoints().isEmpty()
                    ? "" : " 要点:" + String.join(";", a.keyPoints())))
            .collect(Collectors.joining("\n"));

        return chatClient.prompt()
            .system("你在维护某主题的「演进式综述」。把同主题的多篇文章织成一篇有逻辑、连贯的概览:"
                + "呈现该主题的脉络、共识与分歧,而非简单罗列每篇。直接输出综述正文,300-500 字中文,不要标题。")
            .user(u -> u.text("主题:{topic}\n该主题下的文章:\n{body}")
                        .param("topic", topic)
                        .param("body", body))
            .call()
            .content();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
