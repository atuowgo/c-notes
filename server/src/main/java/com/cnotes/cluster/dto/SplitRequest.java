package com.cnotes.cluster.dto;

import lombok.Data;
import java.util.List;

@Data
public class SplitRequest {
    private String sourceId;
    private String name;
    private List<String> articleIds;
}
