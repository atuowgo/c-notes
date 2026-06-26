package com.cnotes.link;

import com.cnotes.article.entity.Article;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 关联理由生成:用 ChatClient(DeepSeek)为两篇文章写一句"为什么相关"。
 * 包成独立 @Service 便于测试打桩(同 ClusterSummarizer/AutoClusterSummarizer 思路),
 * 隔离 chat 网络。无 DEEPSEEK_API_KEY / 调用失败 → 优雅降级空串,不阻断关联落库。
 */
@Service
public class LinkReasoner {

    private final ChatClient chatClient;

    public LinkReasoner(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** 输出一句中文"为什么相关"(<=30 字);无 key / 异常时空串。 */
    public String reason(Article source, Article target) {
        try {
            return chatClient.prompt()
                .system("你在为两篇文章写一句「为什么相关」的简短说明。直接输出一句中文,"
                    + "不超过 30 字,不要前缀、不要引号。")
                .user(u -> u.text("文章甲:《{a}》摘要:{as}\n文章乙:《{b}》摘要:{bs}")
                    .param("a", nz(source.getTitle()))
                    .param("as", nz(source.getSummary()))
                    .param("b", nz(target.getTitle()))
                    .param("bs", nz(target.getSummary())))
                .call()
                .content();
        } catch (Exception e) {
            return "";   // 无 DEEPSEEK_API_KEY / 网络异常:优雅降级空串
        }
    }

    /**
     * 更深入判定+理由:判断 target 是否比 source 更深入(同主题但更详尽/更深挖)。
     * 返回一句中文"为什么更深入"(<=30 字);LLM 判定「否」时返回 null(调用方据此跳过该候选)。
     * 无 DEEPSEEK_API_KEY / 调用失败 → 优雅降级返回"同主题深挖"(保留候选,不阻断更深入落库)。
     */
    public String deeperReason(Article source, Article target) {
        try {
            String out = chatClient.prompt()
                .system("你在判断文章乙相对文章甲是否「更深入」(同主题但更详尽、更深挖)。"
                    + "若是,直接输出一句中文说明,不超过 30 字,不要前缀、不要引号;"
                    + "若否,只输出「否」。")
                .user(u -> u.text("文章甲:《{a}》摘要:{as}\n文章乙:《{b}》摘要:{bs}")
                    .param("a", nz(source.getTitle()))
                    .param("as", nz(source.getSummary()))
                    .param("b", nz(target.getTitle()))
                    .param("bs", nz(target.getSummary())))
                .call()
                .content();
            if (out == null) return "同主题深挖";          // 极少发生:视作可降级保留
            String t = out.trim();
            if (t.isEmpty() || t.startsWith("否")) return null;   // LLM 判定非更深入 → 跳过
            return t;
        } catch (Exception e) {
            return "同主题深挖";   // 无 key / 网络异常:优雅降级,保留候选
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
