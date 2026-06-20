package com.cnotes.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.auth.UserContext;
import com.cnotes.chat.vector.KnowledgeRetriever;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 深聊上下文编排:围绕一篇锚定文章,把多路上下文织成给 ChatClient 的 system 提示。
 * <ul>
 *   <li><b>源1 📄 本文 + 批注</b>:文章标题/摘要/要点,以及该文下的批注引文与想法。</li>
 *   <li><b>源2 🕸 知识网</b>:用问题在向量库检索命中的主题簇 living_summary。</li>
 *   <li><b>源3 🌐 联网搜索</b>:由 ChatClient 通过 {@code WebSearchTool} 自行决定调用,<i>不在此预取</i>。</li>
 * </ul>
 * 返回 {@link ChatContext} 同时记录「实际启用了哪些源」,供消息 sources 标签展示。
 */
@Component
@RequiredArgsConstructor
public class ChatContextAssembler {

    private final ArticleMapper articleMapper;
    private final NoteMapper noteMapper;
    private final KnowledgeRetriever knowledgeRetriever;
    private final ObjectMapper om;

    @Value("${chat.knowledge.top-k:3}")
    private int knowledgeTopK;

    /** 织好的上下文:system 文本 + 实际启用的源标签(📄/🕸,源3 由对话期工具调用追加)。 */
    public record ChatContext(String systemText, List<String> sources) {}

    public ChatContext assemble(String articleId, String question) {
        return assemble(articleId, question, null);
    }

    public ChatContext assemble(String articleId, String question, String noteId) {
        List<String> sources = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        // 源4(由「提问」带入):把某条想法作为本次提问的起点 —— 💭 我的想法
        if (noteId != null && !noteId.isBlank()) {
            Note seed = noteMapper.selectById(noteId);
            if (seed != null) {
                sources.add("💭");
                sb.append("【我的想法(本次提问的起点)】\n");
                if (notBlank(seed.getQuote())) sb.append("引文:「").append(seed.getQuote().trim()).append("」\n");
                if (notBlank(seed.getThought())) sb.append("想法:").append(seed.getThought().trim()).append("\n");
            }
        }

        // 源1:本文 + 批注
        Article a = (articleId == null || articleId.isBlank()) ? null : articleMapper.selectById(articleId);
        if (a != null) {
            sources.add("📄");
            sb.append("【本文】标题:").append(nz(a.getTitle())).append("\n");
            if (notBlank(a.getSummary())) sb.append("摘要:").append(a.getSummary().trim()).append("\n");
            List<String> points = parsePoints(a.getKeyPoints());
            if (!points.isEmpty()) {
                sb.append("要点:\n");
                for (String p : points) sb.append(" - ").append(p).append("\n");
            }
            // 多用户隔离:只取「我自己」的批注,避免把他人公开批注当成「我的批注」混入上下文。
            List<Note> notes = noteMapper.selectList(
                Wrappers.<Note>lambdaQuery()
                    .eq(Note::getArticleId, articleId)
                    .eq(Note::getOwnerId, UserContext.currentOrSystem()));
            if (!notes.isEmpty()) {
                sb.append("【我的批注】\n");
                for (Note n : notes) {
                    if (notBlank(n.getQuote())) sb.append("引文:「").append(n.getQuote().trim()).append("」");
                    if (notBlank(n.getThought())) sb.append(" 想法:").append(n.getThought().trim());
                    sb.append("\n");
                }
            }
        }

        // 源2:知识网向量检索(按当前用户隔离,只召回自己的簇综述)
        List<KnowledgeRetriever.Hit> hits =
            knowledgeRetriever.retrieve(question, knowledgeTopK, UserContext.currentOrSystem());
        if (hits != null && !hits.isEmpty()) {
            sources.add("🕸");
            sb.append("【知识网·相关主题沉淀】\n");
            for (KnowledgeRetriever.Hit h : hits) {
                sb.append("· ").append(nz(h.tagName())).append(":").append(nz(h.summary())).append("\n");
            }
        }

        // 源3:由 ChatClient 通过 WebSearchTool 决定是否调用,不在此预取。
        return new ChatContext(sb.toString().trim(), sources);
    }

    private List<String> parsePoints(String json) {
        try {
            return json == null || json.isBlank()
                ? List.of()
                : om.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
