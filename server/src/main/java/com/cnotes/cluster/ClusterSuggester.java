package com.cnotes.cluster;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 语义建议簇:让 LLM 在现有(未归档)标签未覆盖的盲区里,提议最多 3 个新主题分组。
 * 仅做"提议",落地由用户在前端确认(accept-suggestion)。
 */
@Service
public class ClusterSuggester {

    private final ChatClient chatClient;

    public ClusterSuggester(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** 待分组的文章简介。 */
    public record ArticleBrief(String id, String title, String summary) {}

    /** LLM 结构化返回。 */
    public record Suggestion(String name, String reason, List<String> articleIds) {}
    public record Suggestions(List<Suggestion> groups) {}

    public Suggestions suggest(List<ArticleBrief> articles, List<String> existingTagNames) {
        String body = articles.stream()
            .map(a -> "[" + a.id() + "] 《" + nz(a.title()) + "》:" + nz(a.summary()))
            .collect(Collectors.joining("\n"));
        String existing = existingTagNames.isEmpty() ? "(暂无)" : String.join("、", existingTagNames);

        return chatClient.prompt()
            .system("你在帮用户发现「现有标签未很好覆盖」的潜在主题簇。阅读文章清单后,提议最多 3 个"
                + "新的主题分组:每个含一个简短主题名(不要与现有标签重复)、一句中文理由、以及属于该主题的文章 id 列表"
                + "(只用清单里出现过的 id)。若没有明显的新主题,返回空列表。")
            .user(u -> u.text("现有标签:{existing}\n文章清单(每行 [id] 标题:摘要):\n{body}")
                        .param("existing", existing)
                        .param("body", body))
            .call()
            .entity(Suggestions.class);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
