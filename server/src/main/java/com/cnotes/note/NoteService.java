package com.cnotes.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.auth.UserContext;
import com.cnotes.note.dto.*;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteMapper noteMapper;
    private final ArticleMapper articleMapper;
    private final AnchorRelocator anchorRelocator;
    private final ObjectMapper om;

    /** 一次重定位的结果统计:命中重定位 / 孤立(找不到 quote)的想法数。 */
    public record RelocationResult(int relocated, int orphaned) {}

    /**
     * 正文更新后,把本文所有带锚点的想法按 quote 在新正文里重定位(产品设计 §8.4)。
     * 找到 → 更新偏移;找不到 → 置空锚点(孤立想法,只存不高亮)。无锚点的想法跳过。
     */
    @Transactional
    public RelocationResult relocateAnchors(String articleId, String newContent) {
        List<Note> notes = noteMapper.selectList(
            Wrappers.<Note>lambdaQuery().eq(Note::getArticleId, articleId));
        int relocated = 0, orphaned = 0;
        for (Note n : notes) {
            NoteAnchor old = readAnchor(n.getAnchor());
            if (old == null) continue;   // 本就无锚点,不处理
            var next = anchorRelocator.relocate(n.getQuote(), old, newContent);
            String newAnchorJson = next.map(this::writeAnchor).orElse(null);
            // 偏移无变化则不写库(避免无谓 update)。
            if (java.util.Objects.equals(newAnchorJson, n.getAnchor())) continue;
            // 用 lambdaUpdate().set 显式写值:孤立时要把 anchor 置 NULL(updateById 会忽略 null)。
            noteMapper.update(null, Wrappers.<Note>lambdaUpdate()
                .eq(Note::getId, n.getId())
                .set(Note::getAnchor, newAnchorJson));
            if (next.isPresent()) relocated++; else orphaned++;
        }
        return new RelocationResult(relocated, orphaned);
    }

    @Transactional
    public NoteDto create(CreateNoteRequest req) {
        Note n = new Note();
        n.setOwnerId(UserContext.currentOrSystem());
        n.setArticleId(req.getArticleId());
        n.setQuote(req.getQuote());
        n.setThought(req.getThought());
        n.setAnchor(writeAnchor(req.getAnchor()));
        n.setVisibility("PRIVATE");   // 端内划线想法默认私有;公开批注走 SocialService
        noteMapper.insert(n);
        return toDto(n, titlesFor(List.of(n.getArticleId())));
    }

    /** 列表:可按 articleId 过滤(本文想法),可按 q 全文检索 quote/thought(跨端可检索)。 */
    public List<NoteDto> list(String articleId, String q) {
        var query = Wrappers.<Note>lambdaQuery()
            .eq(Note::getOwnerId, UserContext.currentOrSystem());
        if (articleId != null && !articleId.isBlank()) {
            query.eq(Note::getArticleId, articleId);
        }
        if (q != null && !q.isBlank()) {
            query.and(w -> w.like(Note::getQuote, q).or().like(Note::getThought, q));
        }
        query.orderByDesc(Note::getCreateTime);
        List<Note> notes = noteMapper.selectList(query);
        Map<String, String> titles =
            titlesFor(notes.stream().map(Note::getArticleId).distinct().toList());
        return notes.stream().map(n -> toDto(n, titles)).toList();
    }

    @Transactional
    public NoteDto update(String id, UpdateNoteRequest req) {
        Note n = noteMapper.selectById(id);
        if (n == null) return null;
        n.setThought(req.getThought());
        noteMapper.updateById(n);
        return toDto(n, titlesFor(List.of(n.getArticleId())));
    }

    @Transactional
    public boolean delete(String id) {
        return noteMapper.deleteById(id) > 0;
    }

    /** 把若干 Note 装配成带文章标题的 DTO(供关联/创作等复用)。 */
    public List<NoteDto> toDtos(List<Note> notes) {
        if (notes.isEmpty()) return List.of();
        Map<String, String> titles =
            titlesFor(notes.stream().map(Note::getArticleId).distinct().toList());
        return notes.stream().map(n -> toDto(n, titles)).toList();
    }

    private Map<String, String> titlesFor(List<String> articleIds) {
        if (articleIds.isEmpty()) return Map.of();
        return articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .select(Article::getId, Article::getTitle)
                .in(Article::getId, articleIds))
            .stream().filter(a -> a.getTitle() != null)
            .collect(Collectors.toMap(Article::getId, Article::getTitle));
    }

    private NoteDto toDto(Note n, Map<String, String> titles) {
        NoteDto d = new NoteDto();
        d.setId(n.getId());
        d.setArticleId(n.getArticleId());
        d.setArticleTitle(titles.get(n.getArticleId()));
        d.setQuote(n.getQuote());
        d.setThought(n.getThought());
        d.setAnchor(readAnchor(n.getAnchor()));
        d.setCreateTime(n.getCreateTime());
        return d;
    }

    private String writeAnchor(NoteAnchor a) {
        if (a == null) return null;
        try { return om.writeValueAsString(a); } catch (Exception e) { return null; }
    }

    private NoteAnchor readAnchor(String json) {
        if (json == null || json.isBlank()) return null;
        try { return om.readValue(json, NoteAnchor.class); } catch (Exception e) { return null; }
    }
}
