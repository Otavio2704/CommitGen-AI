package com.commitgen.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommitResponse {

    private List<Suggestion> suggestions;
    private String model;
    private Long processingTimeMs;

    @Data
    @Builder
    public static class Suggestion {
        private String message;
        private String type;
        private String scope;
        private String description;
    }
}
