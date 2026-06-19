package com.cnotes.compose;

import com.cnotes.compose.dto.ComposeReply;
import com.cnotes.compose.dto.ComposeRequest;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 创作(Compose):把用户选中的若干想法(引文+想法)织成一篇有标题、有段落、连贯的中文创作初稿。
 * 无有效想法时返回友好提示;LLM 异常时降级为本地拼接,始终不抛 500。
 */
@Service
public class ComposeService {

    private static final Logger log = LoggerFactory.getLogger(ComposeService.class);

    private final NoteMapper noteMapper;
    private final ChatClient chatClient;

    public ComposeService(NoteMapper noteMapper, ChatClient.Builder builder) {  // Spring AI 自动配置注入
        this.noteMapper = noteMapper;
        this.chatClient = builder.build();
    }

    public ComposeReply compose(ComposeRequest req) {
        List<String> ids = req == null ? null : req.getNoteIds();
        if (ids == null || ids.isEmpty()) {
            return new ComposeReply("请先选择若干想法作为创作素材。");
        }
        List<Note> notes = noteMapper.selectBatchIds(ids);
        if (notes.isEmpty()) {
            return new ComposeReply("未找到选中的想法,无法创作。请确认所选想法仍然存在。");
        }

        String topic = req.getTopic();
        String material = notes.stream()
            .map(n -> "- 引文:" + nz(n.getQuote()) + " 想法:" + nz(n.getThought()))
            .collect(Collectors.joining("\n"));

        try {
            String draft = chatClient.prompt()
                .system("你是写作助手。请把用户摘录的若干「引文+想法」织成一篇连贯的中文创作初稿:" +
                    "先起一个简短标题,再用若干自然段把这些想法编织成有逻辑、有观点的成文,而非简单罗列。" +
                    "直接输出标题与正文。")
                .user(u -> u.text("创作主题/方向:{topic}\n素材(引文与想法):\n{material}")
                    .param("topic", topic == null || topic.isBlank() ? "(未指定,请自行提炼)" : topic)
                    .param("material", material))
                .call()
                .content();
            if (draft == null || draft.isBlank()) {
                return new ComposeReply(localDraft(topic, material));
            }
            return new ComposeReply(draft);
        } catch (Exception e) {
            log.warn("创作 LLM 调用失败,降级为本地拼接 {}", e.toString());
            return new ComposeReply(localDraft(topic, material));
        }
    }

    private String localDraft(String topic, String material) {
        String title = topic == null || topic.isBlank() ? "创作初稿" : topic;
        return "# " + title + "\n\n" + material;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
