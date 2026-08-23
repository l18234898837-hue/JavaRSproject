package com.htht.multisource.infrastructure.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.application.port.out.model.DispatchReceipt;
import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.application.port.out.model.InspectionResponse;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StorageUri;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorkerResultTranslator {

    private final ObjectMapper objectMapper;

    public WorkerResultTranslator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public InspectionResponse toInspection(DecodedWorkerResponse response) {
        JsonNode payload = response.payload();
        List<ProductCandidate> candidates = new ArrayList<>();
        JsonNode candidateNodes = requiredArray(response, payload, "candidates");
        for (JsonNode candidateNode : candidateNodes) {
            candidates.add(toCandidate(response, candidateNode));
        }
        return new InspectionResponse(requiredText(response, payload, "rulePackageVersion"), candidates);
    }

    public ProcessingPlan toPlan(DecodedWorkerResponse response) {
        JsonNode payload = response.payload();
        List<ProcessingStage> stages = new ArrayList<>();
        for (JsonNode stage : requiredArray(response, payload, "stages")) {
            List<String> dependencies = new ArrayList<>();
            for (JsonNode dependency : requiredArray(response, stage, "dependsOnStageKeys")) {
                dependencies.add(dependency.asText());
            }
            stages.add(new ProcessingStage(
                requiredText(response, stage, "stageKey"),
                requiredText(response, stage, "stageCode"),
                dependencies
            ));
        }
        return new ProcessingPlan(requiredText(response, payload, "version"), stages);
    }

    public DispatchReceipt toDispatchReceipt(DecodedWorkerResponse response) {
        JsonNode payload = response.payload();
        if (!"SUCCEEDED".equals(requiredText(response, payload, "status"))) {
            throw failure(response, "status must be SUCCEEDED for a synchronous terminal response");
        }
        List<ArtifactCandidate> artifacts = new ArrayList<>();
        for (JsonNode artifact : requiredArray(response, payload, "artifacts")) {
            artifacts.add(new ArtifactCandidate(
                new StorageUri(requiredText(response, artifact, "uri")),
                requiredText(response, artifact, "checksum"),
                requiredText(response, artifact, "type"),
                requiredLong(response, artifact, "sizeBytes")
            ));
        }
        return new DispatchReceipt(requiredText(response, payload, "workerId"), true, artifacts);
    }

    private ProductCandidate toCandidate(DecodedWorkerResponse response, JsonNode node) {
        JsonNode profileKey = requiredObject(response, node, "profileKey");
        List<InputAsset> files = new ArrayList<>();
        for (JsonNode file : requiredArray(response, node, "files")) {
            JsonNode reference = requiredObject(response, file, "reference");
            JsonNode storageUri = requiredObject(response, file, "storageUriSnapshot");
            files.add(new InputAsset(
                requiredText(response, file, "role"), requiredInt(response, file, "ordinal"),
                new AssetReference(requiredText(response, reference, "sourceType"), requiredText(response, reference, "sourceId")),
                new StorageUri(requiredText(response, storageUri, "value")),
                requiredText(response, file, "checksum"), requiredBoolean(response, file, "required")
            ));
        }
        return new ProductCandidate(
            requiredText(response, node, "packageId"),
            new ProductProfileKey(requiredText(response, profileKey, "productCode"), requiredText(response, profileKey, "profileVersion")),
            enumValue(response, node, "dataShape", ProductDataShape.class),
            enumValue(response, node, "supportStatus", ProductSupportStatus.class),
            files, objectJson(response, node, "metadataSnapshot"), requiredDouble(response, node, "confidence")
        );
    }

    private String objectJson(DecodedWorkerResponse response, JsonNode parent, String field) {
        JsonNode value = requiredObject(response, parent, field);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw failure(response, field + " cannot be serialized");
        }
    }

    private static JsonNode requiredObject(DecodedWorkerResponse response, JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw failure(response, field + " must be an object");
        }
        return value;
    }

    private static JsonNode requiredArray(DecodedWorkerResponse response, JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw failure(response, field + " must be an array");
        }
        return value;
    }

    private static String requiredText(DecodedWorkerResponse response, JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw failure(response, field + " must be non-blank text");
        }
        return value.asText();
    }

    private static int requiredInt(DecodedWorkerResponse response, JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw failure(response, field + " must be an integer");
        }
        return value.asInt();
    }

    private static long requiredLong(DecodedWorkerResponse response, JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() < 0) {
            throw failure(response, field + " must be a non-negative integer");
        }
        return value.asLong();
    }

    private static boolean requiredBoolean(DecodedWorkerResponse response, JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw failure(response, field + " must be boolean");
        }
        return value.asBoolean();
    }

    private static double requiredDouble(DecodedWorkerResponse response, JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isNumber()) {
            throw failure(response, field + " must be numeric");
        }
        return value.asDouble();
    }

    private static <T extends Enum<T>> T enumValue(
        DecodedWorkerResponse response, JsonNode parent, String field, Class<T> enumType
    ) {
        try {
            return Enum.valueOf(enumType, requiredText(response, parent, field));
        } catch (IllegalArgumentException exception) {
            throw failure(response, field + " has unsupported value");
        }
    }

    private static WorkerProtocolException failure(DecodedWorkerResponse response, String message) {
        return new WorkerProtocolException(response.requestId(), response.operation(), message);
    }
}
