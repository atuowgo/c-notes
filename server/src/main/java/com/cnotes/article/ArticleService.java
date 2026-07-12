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
 * 另:正文 HTML(contentHtml)一律落盘到 StorageService(记 html_object_key),不设阈值——
 * 它是纯渲染产物、体量与 content 无必然关系,统一落盘更简单;取详情按 key 读回。
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

    /** HTML 落盘(幂等):已落盘(html_object_key 非空)仅清瞬态;否则落盘并记 key。HTML 一律落盘不看阈值。 */
    public void offloadHtml(Article a) {
        if (a.getHtmlObjectKey() != null) { a.setContentHtml(null); return; }
        String h = a.getContentHtml();
        if (h == null || h.isBlank()) return;
        String key = UUID.randomUUID().toString().replace("-", "");
        storageService.put(key, h);
        a.setHtmlObjectKey(key);
        a.setContentHtml(null);
    }

    /** 取详情:有 html_object_key 则从存储读回 HTML 填瞬态字段。 */
    public void hydrateHtml(Article a) {
        if (a == null || a.getHtmlObjectKey() == null) return;
        String h = storageService.get(a.getHtmlObjectKey());
        if (h != null) a.setContentHtml(h);
    }

    /** 新增:先落盘决策再 insert。 */
    // 注:offloadContent/offloadHtml 的 put 是本地文件写,不在 DB 事务内;若后一步 put 或 DB 写失败,
    // 前面已落盘的 blob 会成孤儿(A2 起既有的已知限制,靠运维侧清理)。
    public void save(Article a) {
        offloadContent(a);
        offloadHtml(a);
        articleMapper.insert(a);
    }

    /** 更新:先落盘决策(幂等)再 updateById。 */
    public void update(Article a) {
        offloadContent(a);
        offloadHtml(a);
        articleMapper.updateById(a);
    }
}
