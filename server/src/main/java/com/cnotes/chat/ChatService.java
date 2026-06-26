package com.cnotes.chat;

import com.cnotes.chat.dto.ChatReply;
import com.cnotes.chat.dto.ChatRequest;
import com.cnotes.chat.entity.ChatMessage;
import com.cnotes.chat.entity.ChatSession;
import com.cnotes.chat.mapper.ChatMessageMapper;
import com.cnotes.chat.mapper.ChatSessionMapper;
import com.cnotes.chat.tool.WebSearchTool;
import com.cnotes.user.CurrentUserResolver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

/**
 * 深聊编排:确保/创建会话 → 用 {@link ChatContextAssembler} 织好多源上下文 → 交给注入了
 * {@link WebSearchTool}(源3)的 {@link ChatClient} 生成回复 → 落库 user/assistant 两条消息,
 * sources 标签写在 assistant 行,并随 {@link ChatReply} 返回。
 *
 * <p>ChatClient 由 Spring AI 自动配置(指向 DeepSeek);源3 由模型按需调用 WebSearchTool。
 */
@Service
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatContextAssembler assembler;
    private final WebSearchTool webSearchTool;
    private final ChatClient chatClient;
    private final ObjectMapper om;
    private final CurrentUserResolver currentUser;

    public ChatService(ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper,
                       ChatContextAssembler assembler,
                       WebSearchTool webSearchTool,
                       ChatClient.Builder builder,
                       ObjectMapper om,
                       CurrentUserResolver currentUser) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.assembler = assembler;
        this.webSearchTool = webSearchTool;
        this.chatClient = builder.build();
        this.om = om;
        this.currentUser = currentUser;
    }

    public ChatReply chat(String articleId, ChatRequest req) {
        String message = req == null ? null : req.message();
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }

        ChatSession session = ensureSession(articleId, req.sessionId(), message);

        ChatContextAssembler.ChatContext ctx = assembler.assemble(articleId, message);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        if (ctx.systemText() != null && !ctx.systemText().isBlank()) {
            spec = spec.system(ctx.systemText());
        }
        String reply = spec.user(message).tools(webSearchTool).call().content();

        persist(session.getId(), "user", message, null);
        persist(session.getId(), "assistant", reply, writeSources(ctx.sources()));

        return new ChatReply(session.getId(), reply, ctx.sources());
    }

    private ChatSession ensureSession(String articleId, String sessionId, String message) {
        String oid = currentUser.currentUserId();
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSession existing = sessionMapper.selectById(sessionId);
            // 仅当会话存在且属于当前用户时复用,否则新建(防越权续接他人会话)
            if (existing != null && Objects.equals(existing.getOwnerId(), oid)) return existing;
        }
        ChatSession s = new ChatSession();
        s.setOwnerId(oid);
        s.setArticleId(articleId);
        s.setTitle(message.length() > 30 ? message.substring(0, 30) : message);
        sessionMapper.insert(s);
        return s;
    }

    private void persist(String sessionId, String role, String content, String sources) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setSources(sources);
        messageMapper.insert(m);
    }

    private String writeSources(List<String> sources) {
        try {
            return sources == null || sources.isEmpty() ? null : om.writeValueAsString(sources);
        } catch (Exception e) {
            return null;
        }
    }
}
