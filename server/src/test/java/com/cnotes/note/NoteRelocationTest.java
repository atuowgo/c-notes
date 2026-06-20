package com.cnotes.note;

import com.cnotes.note.dto.CreateNoteRequest;
import com.cnotes.note.dto.NoteAnchor;
import com.cnotes.note.dto.NoteDto;
import com.cnotes.note.entity.Note;
import com.cnotes.note.mapper.NoteMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锚点重定位的库集成(H2):正文更新后,relocateAnchors 按 quote 把命中想法的锚点更新到新偏移,
 * 把找不到 quote 的想法锚点置空(孤立)。
 */
@SpringBootTest
@Transactional
class NoteRelocationTest {

    @Autowired NoteService noteService;
    @Autowired NoteMapper noteMapper;

    private static final String ARTICLE = "relocate-art-0000000000000000000";

    private String seedNote(String quote, int start, int end) {
        CreateNoteRequest req = new CreateNoteRequest();
        req.setArticleId(ARTICLE);
        req.setQuote(quote);
        req.setThought("想法-" + quote);
        req.setAnchor(new NoteAnchor(start, end));
        return noteService.create(req).getId();
    }

    @Test
    void relocatesHitAndOrphansMiss() {
        // 原正文:"自注意力让任意两个位置直接交互。残差连接稳定训练。"
        String hitId = seedNote("自注意力", 0, 4);
        String missId = seedNote("残差连接", 12, 16);

        // 新正文:前面插了导言(quote「自注意力」右移),且删掉了「残差连接」那句。
        String newContent = "【导言】Transformer。\n自注意力让任意两个位置直接交互。到此为止。";

        NoteService.RelocationResult r = noteService.relocateAnchors(ARTICLE, newContent);

        assertThat(r.relocated()).isEqualTo(1);
        assertThat(r.orphaned()).isEqualTo(1);

        // 命中:锚点更新到新偏移,且新偏移确实指向 quote。
        NoteDto hit = byId(hitId);
        assertThat(hit.getAnchor()).isNotNull();
        int s = hit.getAnchor().start();
        assertThat(newContent.substring(s, hit.getAnchor().end())).isEqualTo("自注意力");
        assertThat(s).isEqualTo(newContent.indexOf("自注意力"));

        // 孤立:锚点被置空(只存不高亮)。
        NoteDto miss = byId(missId);
        assertThat(miss.getAnchor()).isNull();
        // DB 里 anchor 列确为 NULL。
        Note raw = noteMapper.selectById(missId);
        assertThat(raw.getAnchor()).isNull();
    }

    @Test
    void unchangedContentLeavesAnchorsIntact() {
        String content = "自注意力让任意两个位置直接交互。";
        String id = seedNote("自注意力", 0, 4);

        NoteService.RelocationResult r = noteService.relocateAnchors(ARTICLE, content);

        assertThat(r.relocated()).isZero();   // 偏移未变,不写库
        assertThat(byId(id).getAnchor().start()).isZero();
    }

    private NoteDto byId(String id) {
        return noteService.list(ARTICLE, null).stream()
            .filter(n -> n.getId().equals(id)).findFirst().orElseThrow();
    }
}
