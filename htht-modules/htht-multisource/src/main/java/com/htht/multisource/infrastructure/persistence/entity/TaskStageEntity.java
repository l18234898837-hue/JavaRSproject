package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("rs_task_stage")
public class TaskStageEntity {
    @TableId(value = "stage_id", type = IdType.INPUT)
    private Long stageId;
    private Long taskId;
    private String stageKey;
    private String executionStatus;
    private Integer attempt;
    private String executionId;
    private String workerId;
    private Instant leaseExpireAt;
    private Instant heartbeatAt;
    private String errorCode;
    private String errorMessage;
    private String logUri;
    private Instant startedAt;
    private Instant finishedAt;
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public Instant getLeaseExpireAt() { return leaseExpireAt; }
    public void setLeaseExpireAt(Instant leaseExpireAt) { this.leaseExpireAt = leaseExpireAt; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(Instant heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getLogUri() { return logUri; }
    public void setLogUri(String logUri) { this.logUri = logUri; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
