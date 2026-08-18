package com.htht.multisource.domain.model;

import java.util.Objects;

public record TaskStage(
    StageId id,
    TaskId taskId,
    String stageKey,
    StageExecutionStatus status,
    int attempt
) {
    public TaskStage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
        if (stageKey == null || stageKey.isBlank() || attempt < 1) {
            throw new IllegalArgumentException("Task stage is invalid.");
        }
    }
}
