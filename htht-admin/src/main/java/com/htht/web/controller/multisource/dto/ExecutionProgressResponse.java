package com.htht.web.controller.multisource.dto;

import java.util.List;
import java.time.Instant;

public record ExecutionProgressResponse(
    long taskId,
    String lifecycleStatus,
    String qualityStatus,
    List<StageProgressResponse> stages,
    List<ArtifactProgressResponse> artifacts
) {
    public record StageProgressResponse(
        long stageId, String stageKey, String status, int attempt, String errorCode, String errorMessage,
        String executionId, Instant startedAt, Instant finishedAt, Long durationMs, String description,
        List<String> declaredOutputs
    ) {
        public StageProgressResponse(long stageId, String stageKey, String status, int attempt) {
            this(stageId, stageKey, status, attempt, null, null, null, null, null, null, null, List.of());
        }
    }

    public record ArtifactProgressResponse(long stageId, String type, String uri, String checksum,
                                           long sizeBytes, String retention, boolean downloadable,
                                           String mediaType, boolean previewable, String displayName) {
        public ArtifactProgressResponse(long stageId, String type, String uri, String checksum) {
            this(stageId, type, uri, checksum, 0L, null, true, null, false, null);
        }
    }
}
