package com.cnotes.cluster.auto;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 异步维护语义簇:周期性跨所有 owner 重算(无 SecurityContext,故遍历 distinctOwnerIds)。
 * 与 {@link com.cnotes.cluster.ClusterSummaryWorker}、ArticleWorker 共用
 * worker.scheduling.enabled 门控(SchedulingConfig):演示/测试态关闭,避免后台轮询污染共享 H2。
 * 呼应"你睡觉时系统在工作,醒来语义簇已经长好"。
 */
@Component
@RequiredArgsConstructor
public class AutoClusterWorker {

    private static final Logger log = LoggerFactory.getLogger(AutoClusterWorker.class);

    private final AutoClusterService autoClusterService;

    @Scheduled(fixedDelayString = "${cluster.auto-poll-ms:30000}")
    public void refreshAutoClusters() {
        for (String ownerId : autoClusterService.distinctOwnerIds()) {
            try {
                autoClusterService.recomputeForOwner(ownerId);
            } catch (Exception e) {
                log.warn("语义簇重算失败 owner={} : {}", ownerId, e.toString());
            }
        }
    }
}
