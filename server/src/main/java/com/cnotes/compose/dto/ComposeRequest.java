package com.cnotes.compose.dto;

import lombok.Data;
import java.util.List;

@Data
public class ComposeRequest {
    private List<String> noteIds;
    private String topic;          // 可空:创作主题/方向
}
