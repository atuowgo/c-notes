package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 文章持久化网关(A2):超阈值正文落盘到 StorageService,content 列置空、记 content_object_key;
 * 取详情时按 key 从存储读回 content,对调用方透明。短正文照旧存 content 列。
 */
@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final StorageService storageService;
    private final int thresholdChars;

    public ArticleService(ArticleMapper articleMapper, StorageService storageService,
                          @Value("${storage.threshold-chars:20000}") int thresholdChars) {
        this.articleMapper = articleMapper;
        this.storageService = storageService;
        this.thresholdChars = thresholdChars;
    }

    /**
     * 落库前:超阈值正文落盘;幂等——已落盘者(content_object_key 非空)仅清空 content 列,
     * 不重复写、不换 key(content 是 hydrate 回来的,避免回写 DB content 列)。
     */
    public void offloadContent(Article a) {
        if (a.getContentObjectKey() != null) {
            a.setContent(null);
            return;
        }
        String c = a.getContent();
        if (c == null || c.length() <= thresholdChars) return;
        String key = UUID.randomUUID().toString().replace("-", "");
        storageService.put(key, c);
        a.setContentObjectKey(key);
        a.setContent(null);
    }

    /** 取详情时:若有 content_object_key,从存储读回 content 填入(对调用方透明)。 */
    public void hydrateContent(Article a) {
        if (a == null || a.getContentObjectKey() == null) return;
        String c = storageService.get(a.getContentObjectKey());
        if (c != null) a.setContent(c);
    }

    /** 新增:先落盘决策再 insert。 */
    public void save(Article a) {
        offloadContent(a);
        articleMapper.insert(a);
    }

    /** 更新:先落盘决策(幂等)再 updateById。 */
    public void update(Article a) {
        offloadContent(a);
        articleMapper.updateById(a);
    }
}
