package com.cnotes.link;

import com.cnotes.link.dto.ArticleLinkDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章关联推荐只读接口:GET /api/articles/{id}/links 返回该文章的关联推荐列表
 * (targetArticle + linkType + reason + score)。路径与 ArticleController 并列,
 * {id}/links 段不会与 ArticleController 的 {id} 冲突(后者仅匹配单段)。
 * 计算由 {@link LinkService} 懒触发(无库存时算并入库),不在此暴露写接口。
 */
@RestController
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;

    @GetMapping("/api/articles/{id}/links")
    public ResponseEntity<List<ArticleLinkDto>> links(@PathVariable String id) {
        return ResponseEntity.ok(linkService.listLinks(id));
    }
}
