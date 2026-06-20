package com.cnotes.share;

import com.cnotes.share.dto.CollectedCardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 分享互动:逐篇分享级别覆盖、收藏(bookmark)、收录(collection)。
 * 写操作要求真实登录,能力门槛按文章生效分享级别校验(见 {@link InteractionService})。
 */
@RestController
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    /** 逐篇覆盖分享级别(仅本人);body: { shareLevel } —— 传空/缺省即清除覆盖回到账号默认。 */
    @PutMapping("/api/articles/{id}/share-level")
    public ResponseEntity<Void> setShareLevel(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String level = body == null ? null : body.get("shareLevel");
        boolean ok = interactionService.setArticleShareLevel(id, level);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/api/articles/{id}/bookmark")
    public ResponseEntity<Void> bookmark(@PathVariable String id) {
        interactionService.bookmark(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/articles/{id}/bookmark")
    public ResponseEntity<Void> unbookmark(@PathVariable String id) {
        interactionService.unbookmark(id);
        return ResponseEntity.noContent().build();
    }

    /** 收录到我的知识库;body: { personalNote } 可选。 */
    @PostMapping("/api/articles/{id}/collect")
    public ResponseEntity<Void> collect(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        interactionService.collect(id, body == null ? null : body.get("personalNote"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/articles/{id}/collect")
    public ResponseEntity<Void> uncollect(@PathVariable String id) {
        interactionService.uncollect(id);
        return ResponseEntity.noContent().build();
    }

    /** 我收录的卡片(渲染进收件箱)。 */
    @GetMapping("/api/collections")
    public List<CollectedCardDto> collections() {
        return interactionService.listCollections();
    }
}
