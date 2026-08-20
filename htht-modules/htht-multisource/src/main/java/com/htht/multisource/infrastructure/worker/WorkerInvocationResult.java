package com.htht.multisource.infrastructure.worker;

import java.util.Objects;

public record WorkerInvocationResult(String requestId, int exitCode, String responseJson) {

    public WorkerInvocationResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(responseJson, "responseJson");
    }
}
