package com.cnotes.relation;

import com.cnotes.relation.dto.RelatedArticleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    @GetMapping("/{id}/related")
    public ResponseEntity<List<RelatedArticleDto>> related(@PathVariable String id) {
        return ResponseEntity.ok(relationService.related(id));
    }
}
