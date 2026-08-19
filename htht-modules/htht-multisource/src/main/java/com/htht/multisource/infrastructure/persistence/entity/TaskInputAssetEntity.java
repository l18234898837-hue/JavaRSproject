package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rs_task_input_asset")
public class TaskInputAssetEntity {

    @TableId(value = "input_asset_id", type = IdType.AUTO)
    private Long inputAssetId;
    private Long taskId;
    private String role;
    private Integer ordinal;
    private String referenceType;
    private String referenceValue;
    private String storageUriSnapshot;
    private String checksum;
    private Boolean required;

    public Long getInputAssetId() { return inputAssetId; }
    public void setInputAssetId(Long inputAssetId) { this.inputAssetId = inputAssetId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getOrdinal() { return ordinal; }
    public void setOrdinal(Integer ordinal) { this.ordinal = ordinal; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public String getReferenceValue() { return referenceValue; }
    public void setReferenceValue(String referenceValue) { this.referenceValue = referenceValue; }
    public String getStorageUriSnapshot() { return storageUriSnapshot; }
    public void setStorageUriSnapshot(String storageUriSnapshot) { this.storageUriSnapshot = storageUriSnapshot; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
}
