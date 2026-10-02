package com.htht.web.controller.multisource.dto;

public record ProcessingStorageSummaryResponse(long availableArtifactCount, long recycleBinBytes,
                                               long totalArtifactBytes, long sourceBytes, long artifactBytes,
                                               long workerBytes, long zipBytes, long reclaimableBytes,
                                               long cleanupFailures) {
    public ProcessingStorageSummaryResponse(long availableArtifactCount, long recycleBinBytes,
                                             long totalArtifactBytes) {
        this(availableArtifactCount, recycleBinBytes, totalArtifactBytes, 0L, totalArtifactBytes,
            0L, 0L, recycleBinBytes, 0L);
    }
}
