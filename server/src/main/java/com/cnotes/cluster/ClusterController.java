package com.cnotes.cluster;

import com.cnotes.cluster.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;

    @GetMapping
    public List<ClusterCardDto> list() {
        return clusterService.listClusters();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClusterDetailDto> detail(@PathVariable String id) {
        ClusterDetailDto d = clusterService.detail(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    /** 手动触发重写综述(演示/纠偏用;日常由后台 worker 自动维护)。 */
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<ClusterDetailDto> regenerate(@PathVariable String id) {
        if (clusterService.detail(id) == null) return ResponseEntity.notFound().build();
        clusterService.regenerate(id);
        return ResponseEntity.ok(clusterService.detail(id));
    }

    /** 纠偏:把一篇文章从本簇移到另一簇。 */
    @PostMapping("/{id}/move-article")
    public ResponseEntity<ClusterDetailDto> moveArticle(@PathVariable String id,
                                                        @RequestBody MoveArticleRequest req) {
        return ResponseEntity.ok(
            clusterService.moveArticle(id, req.getArticleId(), req.getToClusterId()));
    }

    /** 纠偏:合并两个簇(from 并入 to)。 */
    @PostMapping("/merge")
    public ResponseEntity<ClusterDetailDto> merge(@RequestBody MergeRequest req) {
        return ResponseEntity.ok(clusterService.merge(req.getFromId(), req.getToId()));
    }

    /** 纠偏:把某簇的部分文章拆到一个新簇。 */
    @PostMapping("/split")
    public ResponseEntity<ClusterDetailDto> split(@RequestBody SplitRequest req) {
        return ResponseEntity.ok(
            clusterService.split(req.getSourceId(), req.getName(), req.getArticleIds()));
    }

    /** 语义建议簇:LLM 提议现有标签未覆盖的新主题。 */
    @GetMapping("/suggestions")
    public List<ClusterSuggestionDto> suggestions() {
        return clusterService.suggestions();
    }

    /** 采纳一条语义建议簇,落地为新簇。 */
    @PostMapping("/accept-suggestion")
    public ResponseEntity<ClusterDetailDto> acceptSuggestion(@RequestBody AcceptSuggestionRequest req) {
        return ResponseEntity.ok(
            clusterService.acceptSuggestion(req.getName(), req.getArticleIds()));
    }
}
