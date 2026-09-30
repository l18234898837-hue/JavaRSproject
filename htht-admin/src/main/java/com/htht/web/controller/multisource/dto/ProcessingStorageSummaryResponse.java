package com.htht.web.controller.multisource.dto;

public record ProcessingStorageSummaryResponse(long availableArtifactCount, long recycleBinBytes,
                                               long totalArtifactBytes) {
}
