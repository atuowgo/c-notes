package com.cnotes.compose;

import com.cnotes.compose.dto.ComposeReply;
import com.cnotes.compose.dto.ComposeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compose")
@RequiredArgsConstructor
public class ComposeController {

    private final ComposeService composeService;

    /** 创作:从选中的想法组装一篇创作初稿。始终 200(无素材返回友好提示)。 */
    @PostMapping
    public ComposeReply compose(@RequestBody ComposeRequest req) {
        return composeService.compose(req);
    }
}
