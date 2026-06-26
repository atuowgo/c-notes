package com.cnotes.cluster.auto;

import com.cnotes.cluster.auto.dto.AutoClusterCardDto;
import com.cnotes.cluster.auto.dto.AutoClusterDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 语义簇(自动聚类)只读接口:列表 + 详情(含成员文章)。
 * 路径 /api/clusters/auto 与标签簇 /api/clusters 并列;Spring 以字面量段优先于
 * {id} 变量段,故 /api/clusters/auto 不会误命中标签簇的 GET /api/clusters/{id}。
 * 重算由 {@link AutoClusterWorker} 后台周期维护,不在此暴露写接口。
 */
@RestController
@RequestMapping("/api/clusters/auto")
@RequiredArgsConstructor
public class AutoClusterController {

    private final AutoClusterService autoClusterService;

    @GetMapping
    public List<AutoClusterCardDto> list() {
        return autoClusterService.listAutoClusters();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AutoClusterDetailDto> detail(@PathVariable String id) {
        AutoClusterDetailDto d = autoClusterService.detail(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }
}
