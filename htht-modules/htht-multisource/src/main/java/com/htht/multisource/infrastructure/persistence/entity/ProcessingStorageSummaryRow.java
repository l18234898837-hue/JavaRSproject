package com.htht.multisource.infrastructure.persistence.entity;

public class ProcessingStorageSummaryRow {
    private Long availableArtifactCount; private Long recycleBinBytes; private Long totalArtifactBytes;
    public Long getAvailableArtifactCount() { return availableArtifactCount; } public void setAvailableArtifactCount(Long value) { availableArtifactCount = value; }
    public Long getRecycleBinBytes() { return recycleBinBytes; } public void setRecycleBinBytes(Long value) { recycleBinBytes = value; }
    public Long getTotalArtifactBytes() { return totalArtifactBytes; } public void setTotalArtifactBytes(Long value) { totalArtifactBytes = value; }
}
