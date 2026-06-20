package com.cnotes.collect;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.auth.UserContext;
import com.cnotes.collect.dto.CollectRequest;
import com.cnotes.common.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectService {

    private final ArticleMapper articleMapper;

    @Transactional
    public String collect(CollectRequest req) {
        String owner = UserContext.currentOrSystem();
        String urlHash = Hashing.md5Hex(req.getUrl());
        String existing = findIdByHash(owner, urlHash);
        if (existing != null) return existing;

        Article a = new Article();
        a.setOwnerId(owner);
        a.setUrl(req.getUrl());
        a.setUrlHash(urlHash);
        a.setTitle(req.getTitle());
        a.setAuthor(req.getAuthor());
        a.setContent(req.getContent());
        a.setDomSnapshot(req.getDomSnapshot());   // 二级抓取兜底素材:正文不佳时由模型清洗
        a.setSourceType(req.getSourceType() == null ? "browser" : req.getSourceType());
        a.setExtractMethod(req.getContent() != null ? "readability" : null);
        a.setStatus("pending");
        a.setRetryCount(0);
        try {
            articleMapper.insert(a);
            return a.getId();
        } catch (DuplicateKeyException dup) {
            // 并发同 URL:唯一索引 uk_owner_url 兜底,回查本人已存在记录,保持幂等
            String raced = findIdByHash(owner, urlHash);
            if (raced != null) return raced;
            throw dup;
        }
    }

    /** 幂等回查按所有者隔离:同一 URL 不同用户各存各的副本,互不可见。 */
    private String findIdByHash(String owner, String urlHash) {
        Article existing = articleMapper.selectOne(
            Wrappers.<Article>lambdaQuery()
                .eq(Article::getOwnerId, owner)
                .eq(Article::getUrlHash, urlHash));
        return existing == null ? null : existing.getId();
    }
}
