package com.cnotes.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 仅在 worker.scheduling.enabled=true(默认)时启用 @Scheduled 定时器。
 * 测试环境关闭定时器,避免后台轮询污染共享 H2 库的全表断言;
 * ArticleWorker bean 本身始终存在,单测可直接调用 runOne()。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "worker.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
