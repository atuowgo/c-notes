package com.cnotes.cluster.auto;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 语义簇综述生成:把自动聚到一簇的多篇文章织成一段连贯概览(复用 ChatClient,
 * 参照 {@link com.cnotes.cluster.ClusterSummarizer} 的演进式综述思路)。
 * 与标签簇综述的区别:这里簇无人工命名,主题靠文章标题/摘要归纳,故 prompt 更强调"提炼共同主题"。
 */
@Service
public class AutoClusterSummarizer {

    private final ChatClient chatClient;

    public AutoClusterSummarizer(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public record ArticleBrief(String title, String summary) {}

    /**
     * @param topic 簇标题(取 medoid 文章标题,作为主题提示)
     * @param articles 该簇成员的标题+摘要
     * @return 200-400 字中文综述正文;无标题
     */
    public String summarize(String topic, List<ArticleBrief> articles) {
        String body = articles.stream()
            .map(a -> "- 《" + nz(a.title()) + "》摘要:" + nz(a.summary()))
            .collect(Collectors.joining("\n"));

        return chatClient.prompt()
            .system("你在为一组语义相似的文章写「主题综述」。先归纳这组文章的共同主题,"
                + "再呈现主要观点、共识与差异,而非逐篇罗列。直接输出综述正文,200-400 字中文,不要标题。")
            .user(u -> u.text("主题提示:{topic}\n该簇的文章:\n{body}")
                        .param("topic", topic)
                        .param("body", body))
            .call()
            .content();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
