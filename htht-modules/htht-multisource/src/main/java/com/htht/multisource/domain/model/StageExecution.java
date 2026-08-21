package com.htht.multisource.domain.model;

import java.time.Instant;
import java.util.Objects;

public record StageExecution(
    ExecutionId executionId,
    String workerId,
    Instant leaseExpiresAt,
    String errorCode,
    String errorMessage,
    Instant startedAt,
    Instant finishedAt
) {
    public StageExecution {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        Objects.requireNonNull(startedAt, "startedAt");
        if (workerId != null && workerId.isBlank()) {
            throw new IllegalArgumentException("Worker id must not be blank.");
        }
        if (leaseExpiresAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Stage execution lease must not precede its start.");
        }
        if ((errorCode == null) != (errorMessage == null)) {
            throw new IllegalArgumentException("Execution error code and message must be supplied together.");
        }
    }

    public StageExecution finish(Instant finishedAt) {
        Objects.requireNonNull(finishedAt, "finishedAt");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Stage execution finish must not precede its start.");
        }
        return new StageExecution(executionId, workerId, leaseExpiresAt, errorCode, errorMessage, startedAt, finishedAt);
    }

    public StageExecution withFailure(String code, String message, Instant finishedAt) {
        if (code == null || code.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Execution failure details must not be blank.");
        }
        return new StageExecution(executionId, workerId, leaseExpiresAt, code, message, startedAt, finishedAt);
    }
}
