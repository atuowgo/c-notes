package com.cnotes.collect;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
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
        String urlHash = Hashing.md5Hex(req.getUrl());
        String existing = findIdByHash(urlHash);
        if (existing != null) return existing;

        Article a = new Article();
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
