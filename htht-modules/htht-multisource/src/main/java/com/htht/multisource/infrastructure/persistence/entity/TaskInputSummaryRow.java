package com.htht.multisource.infrastructure.persistence.entity;

public class TaskInputSummaryRow {
    private Long inputCount;
    private Long sizeBytes;

    public Long getInputCount() { return inputCount; }
    public void setInputCount(Long value) { inputCount = value; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long value) { sizeBytes = value; }
}
