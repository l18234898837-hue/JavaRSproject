package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import java.time.Instant;

public record TaskStageView(
    StageId id,
    String stageKey,
    StageExecutionStatus status,
    int attempt,
    String executionId,
    String errorCode,
    String errorMessage,
    Instant startedAt,
    Instant finishedAt
) {
    public TaskStageView(StageId id, String stageKey, StageExecutionStatus status, int attempt,
                         String errorCode, String errorMessage) {
        this(id, stageKey, status, attempt, null, errorCode, errorMessage, null, null);
    }

    public TaskStageView(StageId id, String stageKey, StageExecutionStatus status, int attempt) {
        this(id, stageKey, status, attempt, null, null, null, null, null);
    }

    public TaskStageView(StageId id, String stageKey, StageExecutionStatus status, int attempt,
                         String executionId, String errorCode, String errorMessage) {
        this(id, stageKey, status, attempt, executionId, errorCode, errorMessage, null, null);
    }

    public TaskStageView(StageId id, String stageKey, StageExecutionStatus status, int attempt,
                         String executionId, String errorCode, String errorMessage,
                         Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.stageKey = stageKey;
        this.status = status;
        this.attempt = attempt;
        this.executionId = executionId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }
}
