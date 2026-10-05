package com.htht.multisource.infrastructure.persistence.entity;

import java.time.Instant;

public class TaskHistoryRow {
    private Long taskId; private String taskNo; private String taskName; private String productCodes;
    private Instant completedAt; private String lifecycleStatus; private Long artifactCount; private Long artifactSizeBytes;
    private String retentionStatus;
    private String sourceRetentionStatus;
    public Long getTaskId() { return taskId; } public void setTaskId(Long value) { taskId = value; }
    public String getTaskNo() { return taskNo; } public void setTaskNo(String value) { taskNo = value; }
    public String getTaskName() { return taskName; } public void setTaskName(String value) { taskName = value; }
    public String getProductCodes() { return productCodes; } public void setProductCodes(String value) { productCodes = value; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant value) { completedAt = value; }
    public String getLifecycleStatus() { return lifecycleStatus; } public void setLifecycleStatus(String value) { lifecycleStatus = value; }
    public Long getArtifactCount() { return artifactCount; } public void setArtifactCount(Long value) { artifactCount = value; }
    public Long getArtifactSizeBytes() { return artifactSizeBytes; } public void setArtifactSizeBytes(Long value) { artifactSizeBytes = value; }
    public String getRetentionStatus() { return retentionStatus; } public void setRetentionStatus(String value) { retentionStatus = value; }
    public String getSourceRetentionStatus() { return sourceRetentionStatus; } public void setSourceRetentionStatus(String value) { sourceRetentionStatus = value; }
}
