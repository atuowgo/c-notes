package com.cnotes.article;

import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.extract.ContentFetcher;
import com.cnotes.note.NoteService;
import com.cnotes.organize.ArticleOrganizer;
import com.cnotes.organize.OrganizeResult;
import com.cnotes.tag.TagClassifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * 刷新正文:重新抓取文章正文。正文变化时,先把本文划线想法的锚点按 quote 重定位到新正文
 * (产品设计 §8.4 —— 正文更新后偏移可能失效),再更新正文并尽力重织摘要/要点/标签。
 * 重织失败不阻断正文与锚点更新(优雅降级)。
 */
@Service
@RequiredArgsConstructor
public class ArticleRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ArticleRefreshService.class);

    private final ArticleMapper articleMapper;
    private final ContentFetcher contentFetcher;
    private final ArticleOrganizer organizer;
    private final TagClassifier tagClassifier;
    private final NoteService noteService;
    private final ObjectMapper om;

    /** 刷新结果:正文是否变化 + 锚点重定位统计。 */
    public record RefreshResult(boolean contentChanged,
                                NoteService.RelocationResult relocation) {}

    @Transactional
    public RefreshResult refresh(String articleId) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) throw new IllegalArgumentException("article not found: " + articleId);

        ContentFetcher.Extracted ex = contentFetcher.fetch(a.getUrl());
        String newContent = ex == null ? null : ex.text();
        if (newContent == null || newContent.isBlank()) {
            throw new IllegalStateException("refresh fetch failed: " + a.getUrl());
        }
        if (newContent.equals(a.getContent())) {
            return new RefreshResult(false, new NoteService.RelocationResult(0, 0));
        }

        // 正文变了:先按新正文重定位锚点(用旧偏移就近匹配 quote),再落新正文。
        NoteService.RelocationResult reloc = noteService.relocateAnchors(articleId, newContent);

        Article upd = new Article();
        upd.setId(articleId);
        upd.setContent(newContent);
        upd.setExtractMethod("refresh");
        // 尽力重织(LLM 不可用时不阻断正文/锚点更新)。
        try {
            OrganizeResult r = organizer.organize(a.getTitle(), newContent, tagClassifier.allowedTagNames(a.getOwnerId()));
            upd.setSummary(r.summary());
            upd.setKeyPoints(om.writeValueAsString(r.keyPoints()));
            tagClassifier.apply(articleId, a.getOwnerId(), r.tags());
            upd.setProcessedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("刷新后重织失败(正文/锚点已更新,综述保持原样)articleId={}: {}", articleId, e.toString());
        }
        articleMapper.updateById(upd);
        return new RefreshResult(true, reloc);
    }
}
