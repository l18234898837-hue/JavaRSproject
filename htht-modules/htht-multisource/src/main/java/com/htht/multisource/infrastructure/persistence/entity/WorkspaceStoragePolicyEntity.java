package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("rs_workspace_storage_policy")
public class WorkspaceStoragePolicyEntity {
    @TableId private String workspaceKey;
    private Integer inputRetentionDays;
    private Integer orphanUploadTtlHours;
    private Integer workerDirectoryTtlHours;
    private Boolean autoCleanupEnabled;
    private Integer sourceRecycleBinRetentionDays;
    private Integer artifactRecycleBinRetentionDays;
    private Long version;
    private Instant updatedAt;

    public String getWorkspaceKey() { return workspaceKey; }
    public void setWorkspaceKey(String value) { workspaceKey = value; }
    public Integer getInputRetentionDays() { return inputRetentionDays; }
    public void setInputRetentionDays(Integer value) { inputRetentionDays = value; }
    public Integer getOrphanUploadTtlHours() { return orphanUploadTtlHours; }
    public void setOrphanUploadTtlHours(Integer value) { orphanUploadTtlHours = value; }
    public Integer getWorkerDirectoryTtlHours() { return workerDirectoryTtlHours; }
    public void setWorkerDirectoryTtlHours(Integer value) { workerDirectoryTtlHours = value; }
    public Boolean getAutoCleanupEnabled() { return autoCleanupEnabled; }
    public void setAutoCleanupEnabled(Boolean value) { autoCleanupEnabled = value; }
    public Integer getSourceRecycleBinRetentionDays() { return sourceRecycleBinRetentionDays; }
    public void setSourceRecycleBinRetentionDays(Integer value) { sourceRecycleBinRetentionDays = value; }
    public Integer getArtifactRecycleBinRetentionDays() { return artifactRecycleBinRetentionDays; }
    public void setArtifactRecycleBinRetentionDays(Integer value) { artifactRecycleBinRetentionDays = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
