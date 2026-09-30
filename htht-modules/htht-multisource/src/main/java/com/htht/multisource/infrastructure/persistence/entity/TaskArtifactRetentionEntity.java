package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("rs_task_artifact_retention")
public class TaskArtifactRetentionEntity {
    @TableId private Long taskId;
    private String retentionStatus;
    private Boolean pinned;
    private Instant recycledAt;
    private Instant purgeStartedAt;
    private Instant purgedAt;
    private Long operatorId;
    private String failureReason;
    private Long version;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public String getRetentionStatus() { return retentionStatus; }
    public void setRetentionStatus(String value) { retentionStatus = value; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean value) { pinned = value; }
    public Instant getRecycledAt() { return recycledAt; }
    public void setRecycledAt(Instant value) { recycledAt = value; }
    public Instant getPurgeStartedAt() { return purgeStartedAt; }
    public void setPurgeStartedAt(Instant value) { purgeStartedAt = value; }
    public Instant getPurgedAt() { return purgedAt; }
    public void setPurgedAt(Instant value) { purgedAt = value; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long value) { operatorId = value; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String value) { failureReason = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
