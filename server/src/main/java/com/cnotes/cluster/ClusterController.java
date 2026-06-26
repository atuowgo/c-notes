package com.cnotes.cluster;

import com.cnotes.cluster.dto.ClusterCardDto;
import com.cnotes.cluster.dto.ClusterDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /** 合并簇:source 标签下文章 retag 到 target,删 source 标签/簇;返回合并后的目标簇。 */
    @PostMapping("/merge")
    public ResponseEntity<ClusterDetailDto> merge(@RequestBody MergeRequest req) {
        return ResponseEntity.ok(clusterService.merge(req.sourceId(), req.targetId()));
    }

    /** 拆分簇:建新标签,指定文章 retag 到新标签;返回新簇。 */
    @PostMapping("/{id}/split")
    public ResponseEntity<ClusterDetailDto> split(@PathVariable String id, @RequestBody SplitRequest req) {
        return ResponseEntity.ok(clusterService.split(id, req.articleIds(), req.newTag()));
    }

    /** 单篇跨簇移动:把 articleId 从当前簇移到 targetTagId;返回刷新后的当前簇。 */
    @PostMapping("/{id}/move")
    public ResponseEntity<ClusterDetailDto> move(@PathVariable String id, @RequestBody MoveRequest req) {
        return ResponseEntity.ok(clusterService.move(id, req.articleId(), req.targetTagId()));
    }

    /** 参数校验失败(同名簇/源目标相同/不存在等)→ 400,带 message 给前端。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /** 合并请求:源簇 + 目标簇。 */
    public record MergeRequest(String sourceId, String targetId) {}

    /** 拆分请求:从当前簇拆出的文章 + 新簇名。 */
    public record SplitRequest(List<String> articleIds, String newTag) {}

    /** 移动请求:单篇文章 + 目标簇 id。 */
    public record MoveRequest(String articleId, String targetTagId) {}
}
