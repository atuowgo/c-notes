package com.cnotes.collect;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.collect.dto.CollectRequest;
import com.cnotes.common.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectService {

    private final ArticleMapper articleMapper;

    @Transactional
    public String collect(CollectRequest req) {
        String urlHash = Hashing.md5Hex(req.getUrl());
        Article existing = articleMapper.selectOne(
            Wrappers.<Article>lambdaQuery().eq(Article::getUrlHash, urlHash));
        if (existing != null) return existing.getId();

        Article a = new Article();
        a.setUrl(req.getUrl());
        a.setUrlHash(urlHash);
        a.setTitle(req.getTitle());
        a.setAuthor(req.getAuthor());
        a.setContent(req.getContent());
        a.setSourceType(req.getSourceType() == null ? "browser" : req.getSourceType());
        a.setExtractMethod(req.getContent() != null ? "readability" : null);
        a.setStatus("pending");
        a.setRetryCount(0);
        articleMapper.insert(a);
        return a.getId();
    }
}
