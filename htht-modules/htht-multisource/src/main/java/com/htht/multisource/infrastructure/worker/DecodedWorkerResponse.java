package com.htht.multisource.infrastructure.worker;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record DecodedWorkerResponse(String requestId, WorkerOperation operation, JsonNode payload) {

    public DecodedWorkerResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(payload, "payload");
    }
}
