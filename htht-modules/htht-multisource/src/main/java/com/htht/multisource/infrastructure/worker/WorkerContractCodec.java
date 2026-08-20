package com.htht.multisource.infrastructure.worker;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.PlanRequest;

import java.io.IOException;
import java.util.Objects;

public final class WorkerContractCodec {

    private static final String SCHEMA_VERSION = "1.0";

    private final ObjectMapper objectMapper;

    public WorkerContractCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public String encodeStageExecution(String requestId, StageExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("taskId", request.taskId().value());
        payload.put("stageId", request.stageId().value());
        payload.put("stageKey", request.stageKey());
        payload.put("attempt", request.attempt());
        payload.put("executionId", request.executionId().value());
        payload.put("profileCode", request.profileCode());
        payload.put("profileVersion", request.profileVersion());
        payload.put("processorVersion", request.processorVersion());
        payload.put("parameterSnapshot", request.parameterSnapshot());
        ObjectNode executionGrant = payload.putObject("executionGrant");
        executionGrant.put("grantReference", request.executionGrant().grantReference());
        executionGrant.put("expiresAt", request.executionGrant().expiresAt().toString());
        return encodeEnvelope(requestId, WorkerOperation.EXECUTE_STAGE, payload);
    }

    public String encodeInspect(String requestId, InspectionRequest request) {
        Objects.requireNonNull(request, "request");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("subject", objectMapper.valueToTree(request.subject()));
        payload.set("inputs", objectMapper.valueToTree(request.inputs()));
        payload.set("readGrant", encodeGrant(request.readGrant().grantReference(), request.readGrant().expiresAt().toString()));
        return encodeEnvelope(requestId, WorkerOperation.INSPECT, payload);
    }

    public String encodeBuildPlan(String requestId, PlanRequest request) {
        Objects.requireNonNull(request, "request");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("taskId", request.taskId().value());
        payload.set("candidate", objectMapper.valueToTree(request.candidate()));
        payload.put("parameterSnapshot", request.parameterSnapshot());
        payload.set("readGrant", encodeGrant(request.readGrant().grantReference(), request.readGrant().expiresAt().toString()));
        return encodeEnvelope(requestId, WorkerOperation.BUILD_PLAN, payload);
    }

    private ObjectNode encodeGrant(String grantReference, String expiresAt) {
        ObjectNode grant = objectMapper.createObjectNode();
        grant.put("grantReference", grantReference);
        grant.put("expiresAt", expiresAt);
        return grant;
    }

    public DecodedWorkerResponse decodeResponse(String requestId, WorkerOperation expectedOperation, String responseJson) {
        requireRequestId(requestId, expectedOperation);
        Objects.requireNonNull(responseJson, "responseJson");
        try (JsonParser parser = objectMapper.getFactory().createParser(responseJson)) {
            JsonNode root = objectMapper.readTree(parser);
            if (root == null || parser.nextToken() != null) {
                throw new WorkerProtocolException(requestId, expectedOperation, "response must contain one JSON value");
            }
            if (!root.isObject()) {
                throw new WorkerProtocolException(requestId, expectedOperation, "response envelope must be an object");
            }
            if (!SCHEMA_VERSION.equals(root.path("schemaVersion").asText())) {
                throw new WorkerProtocolException(requestId, expectedOperation, "unsupported schema version");
            }
            WorkerOperation operation;
            try {
                operation = WorkerOperation.fromWireValue(root.path("operation").asText());
            } catch (IllegalArgumentException exception) {
                throw new WorkerProtocolException(requestId, expectedOperation, "unknown operation", exception);
            }
            if (operation != expectedOperation) {
                throw new WorkerProtocolException(requestId, expectedOperation, "response operation does not match invocation");
            }
            if (!requestId.equals(root.path("requestId").asText())) {
                throw new WorkerProtocolException(requestId, expectedOperation, "response requestId does not match invocation");
            }
            JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                if (!error.isObject() || !error.path("code").isTextual() || error.path("code").asText().isBlank()
                    || !error.path("message").isTextual() || error.path("message").asText().isBlank()
                    || !error.path("retryable").isBoolean()) {
                    throw new WorkerProtocolException(requestId, expectedOperation, "response error is incomplete");
                }
                throw new WorkerProtocolException(
                    requestId,
                    expectedOperation,
                    "Worker error " + error.path("code").asText() + ": " + error.path("message").asText()
                        + " (retryable=" + error.path("retryable").asBoolean() + ")"
                );
            }
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new WorkerProtocolException(requestId, expectedOperation, "response payload is required");
            }
            return new DecodedWorkerResponse(requestId, operation, payload);
        } catch (IOException exception) {
            throw new WorkerProtocolException(requestId, expectedOperation, "response is not valid JSON", exception);
        }
    }

    private String encodeEnvelope(String requestId, WorkerOperation operation, JsonNode payload) {
        requireRequestId(requestId, operation);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("operation", operation.wireValue());
        root.put("requestId", requestId);
        root.set("payload", payload);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (IOException exception) {
            throw new WorkerProtocolException(requestId, operation, "request cannot be encoded", exception);
        }
    }

    private static void requireRequestId(String requestId, WorkerOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (requestId == null || requestId.isBlank()) {
            throw new WorkerProtocolException(String.valueOf(requestId), operation, "requestId must not be blank");
        }
    }
}
