package com.cnotes.cluster;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 异步维护演进式综述:周期性找出成员变化的簇,重写其综述
 * (呼应"你睡觉时系统在工作,醒来知识网已经长好")。
 * 与文章 Worker 共用 worker.scheduling.enabled 门控:演示/测试态关闭。
 */
@Component
@RequiredArgsConstructor
public class ClusterSummaryWorker {

    private static final Logger log = LoggerFactory.getLogger(ClusterSummaryWorker.class);

    private final ClusterService clusterService;

    @Scheduled(fixedDelayString = "${cluster.summary-poll-ms:15000}")
    public void refreshStale() {
        List<String> stale = clusterService.staleClusterTagIds();
        for (String tagId : stale) {
            try {
                clusterService.regenerate(tagId);
            } catch (Exception e) {
                log.warn("簇综述重写失败 tagId={} : {}", tagId, e.toString());
            }
        }
    }
}
