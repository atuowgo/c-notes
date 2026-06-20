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
    private final ArticleRefreshService refreshService;

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

    /** 刷新正文:重新抓取,正文变化则重定位划线想法锚点并尽力重织;返回最新详情。 */
    @PostMapping("/{id}/refresh")
    public ResponseEntity<ArticleDetailDto> refresh(@PathVariable String id) {
        try {
            refreshService.refresh(id);
        } catch (IllegalArgumentException notFound) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException fetchFailed) {
            return ResponseEntity.unprocessableEntity().build();
        }
        return ResponseEntity.ok(queryService.detail(id));
    }
}
