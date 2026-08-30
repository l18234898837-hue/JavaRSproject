package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;

public record TaskStageView(
    StageId id,
    String stageKey,
    StageExecutionStatus status,
    int attempt,
    String executionId,
    String errorCode,
    String errorMessage
) {
    public TaskStageView(StageId id, String stageKey, StageExecutionStatus status, int attempt,
                         String errorCode, String errorMessage) {
        this(id, stageKey, status, attempt, null, errorCode, errorMessage);
    }

    public TaskStageView(StageId id, String stageKey, StageExecutionStatus status, int attempt) {
        this(id, stageKey, status, attempt, null, null, null);
    }
}
