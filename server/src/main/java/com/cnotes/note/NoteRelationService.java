package com.cnotes.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cnotes.note.dto.NoteDto;
import com.cnotes.note.dto.RelatedNoteDto;
import com.cnotes.note.entity.Note;
import com.cnotes.note.entity.NoteRelation;
import com.cnotes.note.mapper.NoteMapper;
import com.cnotes.note.mapper.NoteRelationMapper;
import com.cnotes.tag.entity.ArticleTag;
import com.cnotes.tag.mapper.ArticleTagMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批注↔批注关联:已有关联直接读取;否则用 LLM 从候选想法中挑出至多 4 条关联并落库。
 * LLM 不可用 / 无可用结果时,降级为"同篇"关联;始终不抛错(最坏返回空列表)。
 */
@Service
public class NoteRelationService {

    private static final Logger log = LoggerFactory.getLogger(NoteRelationService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("呼应", "对立", "延伸", "同主题");
    private static final int CANDIDATE_LIMIT = 12;
    private static final int PICK_LIMIT = 4;

    private final NoteMapper noteMapper;
    private final NoteRelationMapper relationMapper;
    private final ArticleTagMapper articleTagMapper;
    private final NoteService noteService;
    private final ChatClient chatClient;

    public NoteRelationService(NoteMapper noteMapper,
                               NoteRelationMapper relationMapper,
                               ArticleTagMapper articleTagMapper,
                               NoteService noteService,
                               ChatClient.Builder builder) {  // Spring AI 自动配置注入
        this.noteMapper = noteMapper;
        this.relationMapper = relationMapper;
        this.articleTagMapper = articleTagMapper;
        this.noteService = noteService;
        this.chatClient = builder.build();
    }

    /** LLM 选出的关联(结构化输出)。 */
    public record Picked(String toNoteId, String relationType, String reason) {}
    public record PickedList(List<Picked> relations) {}

    @Transactional
    public List<RelatedNoteDto> related(String noteId) {
        // 1) 已有关联:直接装配返回
        List<NoteRelation> existing = relationMapper.selectList(
            Wrappers.<NoteRelation>lambdaQuery().eq(NoteRelation::getFromNoteId, noteId));
        if (!existing.isEmpty()) {
            return assemble(existing);
        }

        Note self = noteMapper.selectById(noteId);
        if (self == null) return List.of();

        List<Note> candidates = candidates(self);
        if (candidates.isEmpty()) return List.of();

        // 2) 尝试 LLM 挑选
        List<NoteRelation> picked = tryLlmPick(self, candidates);
        if (picked.isEmpty()) {
            // 3) 降级:同篇文章的其它想法
            picked = fallbackSameArticle(self, candidates);
        }
        if (picked.isEmpty()) return List.of();

        for (NoteRelation r : picked) {
            try { relationMapper.insert(r); } catch (Exception e) { log.warn("插入关联失败 {}", e.toString()); }
        }
        return assemble(picked);
    }

    private List<NoteRelation> tryLlmPick(Note self, List<Note> candidates) {
        try {
            Map<String, Note> byId = candidates.stream()
                .collect(Collectors.toMap(Note::getId, n -> n));
            String candText = candidates.stream()
                .map(n -> "[" + n.getId() + "] 引文:" + nz(n.getQuote())
                    + " 想法:" + nz(n.getThought()))
                .collect(Collectors.joining("\n"));

            PickedList out = chatClient.prompt()
                .system("你在帮用户梳理读书/读文想法之间的关联。给定一条「当前想法」和若干「候选想法」," +
                    "从候选中挑出至多 4 条与当前想法确有关联的,给出 relationType(只能是:呼应、对立、延伸、同主题)" +
                    "和一句话中文理由 reason。toNoteId 必须取自候选的方括号 id。没有合适的就返回空数组。")
                .user(u -> u.text("当前想法:引文:{quote} 想法:{thought}\n候选想法:\n{cands}")
                    .param("quote", nz(self.getQuote()))
                    .param("thought", nz(self.getThought()))
                    .param("cands", candText))
                .call()
                .entity(PickedList.class);

            if (out == null || out.relations() == null) return List.of();
            List<NoteRelation> result = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Picked p : out.relations()) {
                if (result.size() >= PICK_LIMIT) break;
                if (p == null || p.toNoteId() == null) continue;
                Note target = byId.get(p.toNoteId());
                if (target == null || !seen.add(p.toNoteId())) continue;
                String type = ALLOWED_TYPES.contains(p.relationType()) ? p.relationType() : "同主题";
                result.add(relation(self.getId(), target.getId(), type, p.reason()));
            }
            return result;
        } catch (Exception e) {
            log.warn("LLM 关联挑选失败,降级处理 {}", e.toString());
            return List.of();
        }
    }

    private List<NoteRelation> fallbackSameArticle(Note self, List<Note> candidates) {
        return candidates.stream()
            .filter(n -> self.getArticleId() != null && self.getArticleId().equals(n.getArticleId()))
            .limit(PICK_LIMIT)
            .map(n -> relation(self.getId(), n.getId(), "同篇", "同一篇文章的其它想法"))
            .toList();
    }

    /**
     * 候选:排除自身,限 12 条;优先与当前想法所属文章共享标签的文章下的想法,
     * 不足则补任意其它想法。
     */
    private List<Note> candidates(Note self) {
        List<String> sharedArticleIds = articlesSharingTag(self.getArticleId());
        LinkedHashSet<Note> picked = new LinkedHashSet<>();

        if (!sharedArticleIds.isEmpty()) {
            picked.addAll(noteMapper.selectList(Wrappers.<Note>lambdaQuery()
                .in(Note::getArticleId, sharedArticleIds)
                .ne(Note::getId, self.getId())
                .orderByDesc(Note::getCreateTime)
                .last("LIMIT " + CANDIDATE_LIMIT)));
        }
        if (picked.size() < CANDIDATE_LIMIT) {
            picked.addAll(noteMapper.selectList(Wrappers.<Note>lambdaQuery()
                .ne(Note::getId, self.getId())
                .orderByDesc(Note::getCreateTime)
                .last("LIMIT " + CANDIDATE_LIMIT)));
        }
        return picked.stream().limit(CANDIDATE_LIMIT).toList();
    }

    /** 与给定文章共享至少一个标签的其它文章(含自身,便于"同主题"判断)。 */
    private List<String> articlesSharingTag(String articleId) {
        if (articleId == null) return List.of();
        List<String> tagIds = articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
                .select(ArticleTag::getTagId)
                .eq(ArticleTag::getArticleId, articleId))
            .stream().map(ArticleTag::getTagId).toList();
        if (tagIds.isEmpty()) return List.of();
        return articleTagMapper.selectList(Wrappers.<ArticleTag>lambdaQuery()
                .select(ArticleTag::getArticleId)
                .in(ArticleTag::getTagId, tagIds))
            .stream().map(ArticleTag::getArticleId).distinct().toList();
    }

    private List<RelatedNoteDto> assemble(List<NoteRelation> relations) {
        List<String> toIds = relations.stream().map(NoteRelation::getToNoteId).distinct().toList();
        if (toIds.isEmpty()) return List.of();
        Map<String, Note> notes = noteMapper.selectBatchIds(toIds).stream()
            .collect(Collectors.toMap(Note::getId, n -> n));
        Map<String, NoteDto> dtos = noteService.toDtos(new ArrayList<>(notes.values())).stream()
            .collect(Collectors.toMap(NoteDto::getId, d -> d));
        List<RelatedNoteDto> result = new ArrayList<>();
        for (NoteRelation r : relations) {
            NoteDto d = dtos.get(r.getToNoteId());
            if (d == null) continue;
            RelatedNoteDto rd = new RelatedNoteDto();
            rd.setNote(d);
            rd.setRelationType(r.getRelationType());
            rd.setReason(r.getReason());
            result.add(rd);
        }
        return result;
    }

    private NoteRelation relation(String from, String to, String type, String reason) {
        NoteRelation r = new NoteRelation();
        r.setFromNoteId(from);
        r.setToNoteId(to);
        r.setRelationType(type);
        r.setReason(reason);
        return r;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
