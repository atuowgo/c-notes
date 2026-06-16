package com.cnotes.article;

import com.cnotes.article.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleQueryService queryService;

    @GetMapping
    public ResponseEntity<List<ArticleCardDto>> inbox(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        ArticleCardPage result = queryService.listInbox(page, size);
        return ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(result.total()))
            .body(result.items());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleDetailDto> detail(@PathVariable String id) {
        ArticleDetailDto d = queryService.detail(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }
}
