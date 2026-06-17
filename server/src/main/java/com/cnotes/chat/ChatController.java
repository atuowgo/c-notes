package com.cnotes.chat;

import com.cnotes.chat.dto.ChatReply;
import com.cnotes.chat.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 深聊入口:{@code POST /api/articles/{id}/chat}。围绕锚定文章发起/续接一轮对话,
 * 返回助手回复与本轮实际启用的源标签。
 */
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/{id}/chat")
    public ResponseEntity<ChatReply> chat(@PathVariable String id, @RequestBody ChatRequest req) {
        return ResponseEntity.ok(chatService.chat(id, req));
    }
}
