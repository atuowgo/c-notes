package com.cnotes.cluster.dto;

import lombok.Data;

@Data
public class MergeRequest {
    private String fromId;
    private String toId;
}
