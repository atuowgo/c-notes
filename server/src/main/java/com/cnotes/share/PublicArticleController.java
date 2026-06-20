package com.cnotes.share;

import com.cnotes.share.dto.PublicArticleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 公开文章只读入口:无需登录即可访问(免登录浏览)。
 * 私有/不可见文章按 404 处理,不泄露存在性。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicArticleController {

    private final InteractionService interactionService;

    @GetMapping("/articles/{id}")
    public ResponseEntity<PublicArticleDto> publicArticle(@PathVariable String id) {
        PublicArticleDto d = interactionService.publicView(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }
}
