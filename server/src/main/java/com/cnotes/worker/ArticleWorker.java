package com.cnotes.worker;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(value = "worker.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ArticleWorker {

    private final ArticleMapper articleMapper;
    private final ArticleProcessor processor;

    @Value("${worker.max-retry:5}") int maxRetry;
    @Value("${worker.backoff-base-seconds:30}") int backoffBase;

    @Scheduled(fixedDelayString = "${worker.poll-interval-ms:5000}")
    public void poll() {
        List<Article> batch = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
            .and(w -> w.eq(Article::getStatus, "pending")
                       .or(q -> q.eq(Article::getStatus, "failed")
                                 .le(Article::getNextRetryTime, LocalDateTime.now())))
            .last("LIMIT 10"));
        for (Article a : batch) {
            if (!claim(a)) continue;
            runOne(a);
        }
    }

    private boolean claim(Article a) {
        Article upd = new Article();
        upd.setId(a.getId());
        upd.setStatus("processing");
        return articleMapper.update(upd, Wrappers.<Article>lambdaUpdate()
            .eq(Article::getId, a.getId())
            .in(Article::getStatus, "pending", "failed")) == 1;
    }

    void runOne(Article a) {
        a.setStatus("processing");
        processor.process(a);   // Task 7 在此外层加 try/catch 退避
    }
}
