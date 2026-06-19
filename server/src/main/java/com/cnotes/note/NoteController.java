package com.cnotes.note;

import com.cnotes.note.dto.CreateNoteRequest;
import com.cnotes.note.dto.NoteDto;
import com.cnotes.note.dto.RelatedNoteDto;
import com.cnotes.note.dto.UpdateNoteRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final NoteRelationService noteRelationService;

    /** 列表:?articleId= 取本文想法;?q= 跨文章检索;都不传则全部想法。 */
    @GetMapping
    public List<NoteDto> list(@RequestParam(required = false) String articleId,
                              @RequestParam(required = false) String q) {
        return noteService.list(articleId, q);
    }

    /** 批注关联:已有关联直接返回,否则即时计算并落库。始终 200(无关联返回空数组)。 */
    @GetMapping("/{id}/related")
    public List<RelatedNoteDto> related(@PathVariable String id) {
        return noteRelationService.related(id);
    }

    @PostMapping
    public NoteDto create(@Valid @RequestBody CreateNoteRequest req) {
        return noteService.create(req);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteDto> update(@PathVariable String id,
                                          @RequestBody UpdateNoteRequest req) {
        NoteDto d = noteService.update(id, req);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return noteService.delete(id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}
