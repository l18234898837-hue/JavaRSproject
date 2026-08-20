package com.htht.multisource.infrastructure.worker;

import java.util.Objects;

public record WorkerInvocation(String requestId, WorkerOperation operation, String requestJson) {

    public WorkerInvocation {
        if (requestId == null || !requestId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Worker requestId must be a simple identifier.");
        }
        Objects.requireNonNull(operation, "operation");
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("Worker request JSON must not be blank.");
        }
    }
}
