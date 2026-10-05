package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("rs_managed_input_file")
public class ManagedInputFileEntity {
    @TableId(value = "managed_file_id", type = IdType.AUTO) private Long managedFileId;
    private String workspaceKey;
    private Long sysFileId;
    private String filePathSnapshot;
    private String checksum;
    private Long sizeBytes;
    private String status;
    private Instant createdAt;
    private Instant lastSeenAt;
    private Long version;

    public Long getManagedFileId() { return managedFileId; }
    public void setManagedFileId(Long value) { managedFileId = value; }
    public String getWorkspaceKey() { return workspaceKey; }
    public void setWorkspaceKey(String value) { workspaceKey = value; }
    public Long getSysFileId() { return sysFileId; }
    public void setSysFileId(Long value) { sysFileId = value; }
    public String getFilePathSnapshot() { return filePathSnapshot; }
    public void setFilePathSnapshot(String value) { filePathSnapshot = value; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String value) { checksum = value; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long value) { sizeBytes = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant value) { lastSeenAt = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
