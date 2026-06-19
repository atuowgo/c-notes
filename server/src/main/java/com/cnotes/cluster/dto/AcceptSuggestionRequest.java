package com.cnotes.cluster.dto;

import lombok.Data;
import java.util.List;

@Data
public class AcceptSuggestionRequest {
    private String name;
    private List<String> articleIds;
}
