package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;

import java.time.Instant;
import java.util.Objects;

public record ExecutionEvent(
    TaskId taskId,
    StageId stageId,
    ExecutionId executionId,
    int attempt,
    long eventSequence,
    Type type,
    String payload,
    Instant occurredAt,
    Acceptance acceptance
) {
    public enum Type {
        SUCCEEDED,
        FAILED,
        CANCELED,
        TIMED_OUT
    }

    public enum Acceptance {
        APPLIED,
        IGNORED
    }

    public ExecutionEvent {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(acceptance, "acceptance");
        if (attempt < 1 || eventSequence < 1 || payload.isBlank()) {
            throw new IllegalArgumentException("Execution event is invalid.");
        }
    }

    public ExecutionEvent withAcceptance(Acceptance acceptance) {
        return new ExecutionEvent(taskId, stageId, executionId, attempt, eventSequence, type, payload, occurredAt, acceptance);
    }
}
