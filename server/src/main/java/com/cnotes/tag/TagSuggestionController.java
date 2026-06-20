package com.cnotes.tag;

import com.cnotes.tag.dto.TagSuggestionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TagSuggestionController {

    private final TagSuggestionService suggestionService;

    /** 列出某文章的 pending 标签建议(阅读页「待确认标签」使用)。 */
    @GetMapping("/api/articles/{articleId}/tag-suggestions")
    public List<TagSuggestionDto> listForArticle(@PathVariable String articleId) {
        return suggestionService.listPending(articleId);
    }

    /** 接受建议:新建受控标签(若不存在)+ 链接文章 + 标记 accepted。 */
    @PostMapping("/api/tags/suggestions/{id}/accept")
    public ResponseEntity<TagSuggestionDto> accept(@PathVariable String id) {
        try {
            return ResponseEntity.ok(suggestionService.accept(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** 拒绝建议:标记 rejected。 */
    @PostMapping("/api/tags/suggestions/{id}/reject")
    public ResponseEntity<TagSuggestionDto> reject(@PathVariable String id) {
        try {
            return ResponseEntity.ok(suggestionService.reject(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
