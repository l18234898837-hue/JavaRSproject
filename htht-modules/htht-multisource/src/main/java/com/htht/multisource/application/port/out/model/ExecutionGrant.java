package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;

import java.time.Instant;
import java.util.Objects;

public record ExecutionGrant(
    String grantReference,
    Instant expiresAt,
    TaskId taskId,
    StageId stageId,
    ExecutionId executionId,
    int attempt
) {
    public ExecutionGrant(String grantReference, Instant expiresAt) {
        this(grantReference, expiresAt, null, null, null, 0);
    }

    public ExecutionGrant {
        if (grantReference == null || grantReference.isBlank()) {
            throw new IllegalArgumentException("Execution grant reference must not be blank.");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        boolean scoped = taskId != null || stageId != null || executionId != null || attempt != 0;
        if (scoped && (taskId == null || stageId == null || executionId == null || attempt < 1)) {
            throw new IllegalArgumentException("Execution grant scope is incomplete.");
        }
    }
}
