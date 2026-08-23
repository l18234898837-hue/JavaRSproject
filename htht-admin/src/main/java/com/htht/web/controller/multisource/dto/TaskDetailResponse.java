package com.htht.web.controller.multisource.dto;

import java.util.List;

public record TaskDetailResponse(
    long taskId,
    long batchId,
    String lifecycleStatus,
    String qualityStatus,
    String planVersion,
    List<CandidateResponse> candidates,
    List<StageResponse> stages,
    List<ArtifactResponse> artifacts
) {
    public record CandidateResponse(
        String packageId,
        String productCode,
        String profileVersion,
        String dataShape,
        String supportStatus,
        double confidence,
        List<InputAssetResponse> inputAssets
    ) {
    }

    public record InputAssetResponse(
        String role,
        int ordinal,
        String sourceType,
        String sourceId,
        String storageUri,
        String checksum,
        boolean required
    ) {
    }

    public record StageResponse(long stageId, String stageKey, String status, int attempt) {
    }

    public record ArtifactResponse(long stageId, String type, String uri, String checksum) {
    }
}
