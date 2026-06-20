package com.cnotes.note;

import com.cnotes.note.dto.NoteAnchor;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锚点重定位纯算法测试:正文增删导致偏移漂移时,按 quote 重新定位;quote 多次出现取离旧偏移最近的;
 * quote 不再存在则返回空(孤立)。
 */
class AnchorRelocatorTest {

    private final AnchorRelocator relocator = new AnchorRelocator();

    @Test
    void relocatesShiftedQuoteToNewOffset() {
        String oldContent = "自注意力让任意两个位置直接交互。";
        String quote = "自注意力";
        NoteAnchor old = new NoteAnchor(0, 4);
        // 新正文前面插入了一段导言,quote 整体右移。
        String newContent = "【导言】Transformer 概述。\n" + oldContent;

        Optional<NoteAnchor> next = relocator.relocate(quote, old, newContent);

        assertThat(next).isPresent();
        int start = next.get().start();
        assertThat(newContent.substring(start, next.get().end())).isEqualTo(quote);
        assertThat(start).isEqualTo(newContent.indexOf(quote));
    }

    @Test
    void picksOccurrenceClosestToOldStart() {
        // quote 出现两次;旧偏移靠后 → 应选靠后的那次。
        String quote = "残差";
        String newContent = "残差连接……(中间很多字)……再次提到残差连接的重要性。";
        int secondIdx = newContent.indexOf("残差", newContent.indexOf("残差") + 1);
        NoteAnchor old = new NoteAnchor(secondIdx + 1, secondIdx + 3);   // 旧偏移靠近第二次

        Optional<NoteAnchor> next = relocator.relocate(quote, old, newContent);

        assertThat(next).isPresent();
        assertThat(next.get().start()).isEqualTo(secondIdx);
    }

    @Test
    void returnsEmptyWhenQuoteGone() {
        Optional<NoteAnchor> next = relocator.relocate(
            "已被删除的段落", new NoteAnchor(0, 6), "全新的正文,完全不含原引文。");
        assertThat(next).isEmpty();
    }

    @Test
    void emptyInputsDegradeToEmpty() {
        assertThat(relocator.relocate(null, new NoteAnchor(0, 1), "abc")).isEmpty();
        assertThat(relocator.relocate("", new NoteAnchor(0, 1), "abc")).isEmpty();
        assertThat(relocator.relocate("a", new NoteAnchor(0, 1), "")).isEmpty();
        assertThat(relocator.relocate("a", new NoteAnchor(0, 1), null)).isEmpty();
    }
}
