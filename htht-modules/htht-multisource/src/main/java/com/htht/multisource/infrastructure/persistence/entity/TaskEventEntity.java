package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.multisource.infrastructure.persistence.typehandler.PostgreSqlJsonbTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.time.Instant;

@TableName(value = "rs_task_event", autoResultMap = true)
public class TaskEventEntity {
    @TableId(value = "event_id", type = IdType.AUTO)
    private Long eventId;
    private Long stageId;
    private String executionId;
    private Integer attempt;
    private Long eventSequence;
    private String eventType;
    private String acceptanceStatus;
    @TableField(value = "payload", jdbcType = JdbcType.OTHER, typeHandler = PostgreSqlJsonbTypeHandler.class)
    private String payload;
    private Instant occurredAt;
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public Long getEventSequence() { return eventSequence; }
    public void setEventSequence(Long eventSequence) { this.eventSequence = eventSequence; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getAcceptanceStatus() { return acceptanceStatus; }
    public void setAcceptanceStatus(String acceptanceStatus) { this.acceptanceStatus = acceptanceStatus; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
