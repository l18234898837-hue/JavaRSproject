package com.htht.multisource.domain.model;

import java.util.Objects;
import java.time.Instant;

public record TaskStage(
    StageId id,
    TaskId taskId,
    String stageKey,
    StageExecutionStatus status,
    int attempt,
    StageExecution execution
) {
    public TaskStage(StageId id, TaskId taskId, String stageKey, StageExecutionStatus status, int attempt) {
        this(id, taskId, stageKey, status, attempt, null);
    }

    public TaskStage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
        if (stageKey == null || stageKey.isBlank() || attempt < 1) {
            throw new IllegalArgumentException("Task stage is invalid.");
        }
        if (status == StageExecutionStatus.RUNNING && execution == null) {
            throw new IllegalArgumentException("Running stage must have an execution context.");
        }
        if (status != StageExecutionStatus.RUNNING && status != StageExecutionStatus.RECOVERY_PENDING
            && status != StageExecutionStatus.SUCCEEDED && status != StageExecutionStatus.FAILED
            && status != StageExecutionStatus.SKIPPED && execution != null) {
            throw new IllegalArgumentException("Pending or retrying stage must not have an execution context.");
        }
    }

    public TaskStage start(ExecutionId executionId, String workerId, Instant startedAt, Instant leaseExpiresAt) {
        if (status != StageExecutionStatus.PENDING && status != StageExecutionStatus.RETRYING) {
            throw new IllegalStateException("Only pending or retrying stages can start.");
        }
        return new TaskStage(id, taskId, stageKey, StageExecutionStatus.RUNNING, attempt,
            new StageExecution(executionId, workerId, leaseExpiresAt, null, null, startedAt, null));
    }

    public TaskStage succeed(ExecutionId executionId, int expectedAttempt, Instant finishedAt) {
        requireCurrentExecution(executionId, expectedAttempt);
        return new TaskStage(id, taskId, stageKey, StageExecutionStatus.SUCCEEDED, attempt,
            execution.finish(finishedAt));
    }

    public TaskStage fail(ExecutionId executionId, int expectedAttempt, String errorCode, String errorMessage,
                          Instant finishedAt) {
        requireCurrentExecution(executionId, expectedAttempt);
        return new TaskStage(id, taskId, stageKey, StageExecutionStatus.FAILED, attempt,
            execution.withFailure(errorCode, errorMessage, finishedAt));
    }

    public TaskStage recover(ExecutionId executionId, int expectedAttempt, Instant finishedAt) {
        requireCurrentExecution(executionId, expectedAttempt);
        return new TaskStage(id, taskId, stageKey, StageExecutionStatus.RECOVERY_PENDING, attempt,
            execution.finish(finishedAt));
    }

    public TaskStage retry() {
        if (status != StageExecutionStatus.FAILED && status != StageExecutionStatus.RECOVERY_PENDING) {
            throw new IllegalStateException("Only failed or recovery-pending stages can retry.");
        }
        return new TaskStage(id, taskId, stageKey, StageExecutionStatus.RETRYING, attempt + 1, null);
    }

    public TaskStage cancel(Instant finishedAt) {
        if (status != StageExecutionStatus.PENDING && status != StageExecutionStatus.RETRYING
            && status != StageExecutionStatus.RECOVERY_PENDING && status != StageExecutionStatus.RUNNING) {
            throw new IllegalStateException("Only active or runnable stages can be canceled.");
        }
        if (status == StageExecutionStatus.RUNNING) {
            return new TaskStage(id, taskId, stageKey, StageExecutionStatus.SKIPPED, attempt,
                execution.withFailure("CANCELED_BY_USER", "Processing task was canceled by its owner.", finishedAt));
        }
        return new TaskStage(id, taskId, stageKey, StageExecutionStatus.SKIPPED, attempt, null);
    }

    private void requireCurrentExecution(ExecutionId expectedExecutionId, int expectedAttempt) {
        if (status != StageExecutionStatus.RUNNING || execution == null
            || attempt != expectedAttempt || !execution.executionId().equals(expectedExecutionId)) {
            throw new IllegalStateException("Stage execution is stale or not running.");
        }
    }
}
