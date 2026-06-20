package com.cnotes.plaza;

import com.cnotes.plaza.dto.PlazaCardDto;
import com.cnotes.plaza.dto.PlazaPage;
import com.cnotes.plaza.dto.PublicProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 精品广场:免登录可浏览。
 * - 发现流:GET /api/plaza/discover(质量分 / 最新,分页)
 * - 用户公开主页:GET /api/plaza/users/{id}、GET /api/plaza/users/{id}/articles
 */
@RestController
@RequestMapping("/api/plaza")
@RequiredArgsConstructor
public class PlazaController {

    private final PlazaService plazaService;

    @GetMapping("/discover")
    public ResponseEntity<List<PlazaCardDto>> discover(
            @RequestParam(defaultValue = "score") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PlazaPage r = plazaService.discover(sort, page, size);
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(r.total())).body(r.items());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<PublicProfileDto> profile(@PathVariable String id) {
        PublicProfileDto d = plazaService.profile(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @GetMapping("/users/{id}/articles")
    public ResponseEntity<List<PlazaCardDto>> userArticles(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PlazaPage r = plazaService.userArticles(id, page, size);
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(r.total())).body(r.items());
    }
}
