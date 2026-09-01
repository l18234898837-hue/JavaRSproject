package com.htht.web.controller.multisource.dto;

import java.util.List;

public record ExecutionProgressResponse(
    long taskId,
    String lifecycleStatus,
    String qualityStatus,
    List<StageProgressResponse> stages,
    List<ArtifactProgressResponse> artifacts
) {
    public record StageProgressResponse(
        long stageId, String stageKey, String status, int attempt, String errorCode, String errorMessage
    ) {
        public StageProgressResponse(long stageId, String stageKey, String status, int attempt) {
            this(stageId, stageKey, status, attempt, null, null);
        }
    }

    public record ArtifactProgressResponse(long stageId, String type, String uri, String checksum) {
    }
}
