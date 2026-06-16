package com.cnotes.cluster;

import com.cnotes.cluster.dto.ClusterCardDto;
import com.cnotes.cluster.dto.ClusterDetailDto;
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
}
