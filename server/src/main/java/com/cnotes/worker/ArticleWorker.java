package com.cnotes.worker;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
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
        try {
            a.setStatus("processing");
            processor.process(a);
        } catch (Exception e) {
            int next = (a.getRetryCount() == null ? 0 : a.getRetryCount()) + 1;
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String msg = String.valueOf(root.getMessage());
            Article upd = new Article();
            upd.setId(a.getId());
            upd.setRetryCount(next);
            upd.setStatus("failed");
            upd.setLastError(msg.substring(0, Math.min(1000, msg.length())));
            if (next < maxRetry) {
                long delay = (long) (backoffBase * Math.pow(2, next - 1)); // 指数退避
                upd.setNextRetryTime(LocalDateTime.now().plusSeconds(delay));
            } else {
                upd.setNextRetryTime(null);   // 达上限,不再重试
            }
            articleMapper.updateById(upd);
        }
    }
}
