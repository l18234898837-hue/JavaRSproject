package com.htht.multisource.infrastructure.persistence.entity;

public class TaskArtifactRetentionScanRow {
    private Long taskId;
    private String storageKey;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
}
