package com.htht.multisource.application.usecase.model;

public record TaskArtifactRetentionScanResult(long createdAvailable, long createdMissing,
                                               long skippedExisting, long unreadableMetadata) {
    public TaskArtifactRetentionScanResult {
        if (createdAvailable < 0 || createdMissing < 0 || skippedExisting < 0 || unreadableMetadata < 0) {
            throw new IllegalArgumentException("Scan counts must not be negative.");
        }
    }
}
