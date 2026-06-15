package com.cnotes.collect;

import com.cnotes.collect.dto.CollectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/collect")
@RequiredArgsConstructor
public class CollectController {

    private final CollectService collectService;

    @PostMapping
    public Map<String, String> collect(@Valid @RequestBody CollectRequest req) {
        return Map.of("id", collectService.collect(req));
    }
}
