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
    public List<ArticleCardDto> inbox() { return queryService.listInbox(); }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleDetailDto> detail(@PathVariable String id) {
        ArticleDetailDto d = queryService.detail(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }
}
