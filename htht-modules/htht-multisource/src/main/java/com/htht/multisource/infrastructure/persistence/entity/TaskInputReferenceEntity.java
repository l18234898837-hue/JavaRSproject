package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("rs_task_input_reference")
public class TaskInputReferenceEntity {
    @TableId(value = "reference_id", type = IdType.AUTO) private Long referenceId;
    private Long taskId;
    private Long managedFileId;
    private String sourceType;
    private String sourceId;
    private String checksum;
    private String referenceStatus;
    private Instant createdAt;
    private Instant releasedAt;
    private Long version;

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long value) { referenceId = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getManagedFileId() { return managedFileId; }
    public void setManagedFileId(Long value) { managedFileId = value; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String value) { sourceId = value; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String value) { checksum = value; }
    public String getReferenceStatus() { return referenceStatus; }
    public void setReferenceStatus(String value) { referenceStatus = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant value) { releasedAt = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
