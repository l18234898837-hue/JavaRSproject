package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("rs_task_input_retention")
public class TaskInputRetentionEntity {
    @TableId private Long taskId;
    private String workspaceKey;
    private String retentionStatus;
    private Instant expiresAt;
    private Instant scheduledAt;
    private Instant purgeStartedAt;
    private Instant purgedAt;
    private Instant recycledAt;
    private Instant recycleExpiresAt;
    private Instant updatedAt;
    private Long operatorId;
    private String failureReason;
    private Long version;

    public Long getTaskId() { return taskId; } public void setTaskId(Long value) { taskId = value; }
    public String getWorkspaceKey() { return workspaceKey; } public void setWorkspaceKey(String value) { workspaceKey = value; }
    public String getRetentionStatus() { return retentionStatus; } public void setRetentionStatus(String value) { retentionStatus = value; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant value) { expiresAt = value; }
    public Instant getScheduledAt() { return scheduledAt; } public void setScheduledAt(Instant value) { scheduledAt = value; }
    public Instant getPurgeStartedAt() { return purgeStartedAt; } public void setPurgeStartedAt(Instant value) { purgeStartedAt = value; }
    public Instant getPurgedAt() { return purgedAt; } public void setPurgedAt(Instant value) { purgedAt = value; }
    public Instant getRecycledAt() { return recycledAt; } public void setRecycledAt(Instant value) { recycledAt = value; }
    public Instant getRecycleExpiresAt() { return recycleExpiresAt; } public void setRecycleExpiresAt(Instant value) { recycleExpiresAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
    public Long getOperatorId() { return operatorId; } public void setOperatorId(Long value) { operatorId = value; }
    public String getFailureReason() { return failureReason; } public void setFailureReason(String value) { failureReason = value; }
    public Long getVersion() { return version; } public void setVersion(Long value) { version = value; }
}
