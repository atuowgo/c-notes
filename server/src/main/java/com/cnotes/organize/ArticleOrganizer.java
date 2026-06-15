package com.cnotes.organize;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ArticleOrganizer {

    private final ChatClient chatClient;

    public ArticleOrganizer(ChatClient.Builder builder) {  // Spring AI 自动配置注入
        this.chatClient = builder.build();
    }

    public OrganizeResult organize(String title, String content, List<String> allowedTags) {
        String allowed = allowedTags.isEmpty() ? "(暂无)" : String.join("、", allowedTags);
        return chatClient.prompt()
            .system("你是知识管理助手。阅读文章后输出:摘要、3-5 条要点、若干标签。" +
                    "标签优先从受控集中选,确有必要才创造新标签。")
            .user(u -> u.text("受控标签集:{allowed}\n标题:{title}\n正文:\n{content}")
                        .param("allowed", allowed)
                        .param("title", title == null ? "" : title)
                        .param("content", content == null ? "" : content))
            .call()
            .entity(OrganizeResult.class);   // 结构化输出:Spring AI 注入格式指令并反序列化为 record
    }
}
