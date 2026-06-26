package com.cnotes.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.article.entity.Article;
import com.cnotes.article.mapper.ArticleMapper;
import com.cnotes.note.dto.*;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import com.cnotes.user.CurrentUserResolver;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteMapper noteMapper;
    private final ArticleMapper articleMapper;
    private final ObjectMapper om;
    private final CurrentUserResolver currentUser;

    @Transactional
    public NoteDto create(CreateNoteRequest req) {
        Note n = new Note();
        n.setOwnerId(currentUser.currentUserId());
        n.setArticleId(req.getArticleId());
        n.setQuote(req.getQuote());
        n.setThought(req.getThought());
        n.setAnchor(writeAnchor(req.getAnchor()));
        noteMapper.insert(n);
        return toDto(n, titlesFor(List.of(n.getArticleId())));
    }

    /** 列表:按当前用户过滤;可按 articleId 过滤(本文想法),可按 q 全文检索 quote/thought。 */
    public List<NoteDto> list(String articleId, String q) {
        String oid = currentUser.currentUserId();
        var query = Wrappers.<Note>lambdaQuery();
        if (oid != null) query.eq(Note::getOwnerId, oid); else query.isNull(Note::getOwnerId);
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
        if (n == null || !Objects.equals(n.getOwnerId(), currentUser.currentUserId())) return null;
        n.setThought(req.getThought());
        noteMapper.updateById(n);
        return toDto(n, titlesFor(List.of(n.getArticleId())));
    }

    @Transactional
    public boolean delete(String id) {
        Note n = noteMapper.selectById(id);
        if (n == null || !Objects.equals(n.getOwnerId(), currentUser.currentUserId())) return false;
        return noteMapper.deleteById(id) > 0;
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
