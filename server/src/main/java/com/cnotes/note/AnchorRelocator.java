package com.cnotes.note;

import com.cnotes.note.dto.NoteAnchor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 锚点重定位(产品设计 §8.4):正文更新后,划线想法原先记录的字符偏移 [start,end) 可能失效。
 * 以划线时保存的 <b>quote 原文</b> 为锚,在新正文里重新定位:
 * <ul>
 *   <li>新正文里能找到 quote → 取与旧 start <b>最接近</b>的那次出现(应对 quote 多次出现),返回新偏移;</li>
 *   <li>找不到(正文改动较大/该段被删) → 返回空,上层据此把锚点置空(孤立想法,只存不高亮)。</li>
 * </ul>
 * 纯函数、无 IO,便于离线确定性测试。
 */
@Component
public class AnchorRelocator {

    /** 在 newContent 中重定位 quote;返回新锚点,找不到返回空。 */
    public Optional<NoteAnchor> relocate(String quote, NoteAnchor oldAnchor, String newContent) {
        if (quote == null || quote.isEmpty() || newContent == null || newContent.isEmpty()) {
            return Optional.empty();
        }
        int oldStart = oldAnchor != null && oldAnchor.start() != null ? oldAnchor.start() : 0;

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        int from = 0;
        while (true) {
            int idx = newContent.indexOf(quote, from);
            if (idx < 0) break;
            int distance = Math.abs(idx - oldStart);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = idx;
            }
            from = idx + 1;   // 允许重叠出现也逐一考察
        }
        if (best < 0) return Optional.empty();
        return Optional.of(new NoteAnchor(best, best + quote.length()));
    }
}
