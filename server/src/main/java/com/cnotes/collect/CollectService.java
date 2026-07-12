package com.cnotes.collect;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.ArticleService;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.collect.dto.CollectRequest;
import com.cnotes.common.Hashing;
import com.cnotes.extract.HtmlSanitizer;
import com.cnotes.user.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectService {

    private final ArticleMapper articleMapper;
    private final ArticleService articleService;
    private final CurrentUserResolver currentUser;
    private final HtmlSanitizer sanitizer;

    @Transactional
    public String collect(CollectRequest req) {
        String urlHash = Hashing.md5Hex(req.getUrl());
        String existing = findIdByHash(urlHash);
        if (existing != null) return existing;

        Article a = new Article();
        a.setOwnerId(currentUser.currentUserId());
        a.setUrl(req.getUrl());
        a.setUrlHash(urlHash);
        a.setTitle(req.getTitle());
        a.setAuthor(req.getAuthor());
        a.setContent(req.getContent());
        // 净化后仅设到瞬态字段;真正落盘(→ htmlObjectKey)在 ArticleService.offloadHtml(Task 5)。
        // 外层非空判断保留 null 语义(不写成 ""),供 Task 5 落盘决策区分"无 HTML"。
        if (req.getContentHtml() != null && !req.getContentHtml().isBlank()) {
            a.setContentHtml(sanitizer.sanitize(req.getContentHtml(), req.getUrl()));
        }
        a.setSourceType(req.getSourceType() == null ? "browser" : req.getSourceType());
        a.setExtractMethod(req.getContent() != null ? "readability" : null);
        a.setStatus("pending");
        a.setRetryCount(0);
        try {
            articleService.save(a);   // 落盘决策(超阈值正文)+ insert
            return a.getId();
        } catch (DuplicateKeyException dup) {
            // 并发同 URL:唯一索引 uk_url_hash 兜底,回查已存在记录,保持幂等
            String raced = findIdByHash(urlHash);
            if (raced != null) return raced;
            throw dup;
        }
    }

    private String findIdByHash(String urlHash) {
        Article existing = articleMapper.selectOne(
            Wrappers.<Article>lambdaQuery().eq(Article::getUrlHash, urlHash));
        return existing == null ? null : existing.getId();
    }
}
