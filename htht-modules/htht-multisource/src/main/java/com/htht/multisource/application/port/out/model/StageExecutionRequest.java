package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;

import java.util.List;
import java.util.Objects;

public record StageExecutionRequest(
    TaskId taskId,
    StageId stageId,
    String stageKey,
    int attempt,
    ExecutionId executionId,
    String schemaVersion,
    String profileCode,
    String profileVersion,
    String processorVersion,
    String parameterSnapshot,
    List<InputAsset> inputs,
    StorageUri outputUri,
    ExecutionGrant executionGrant,
    String planVersion,
    List<StageOutput> outputs
) {
    public StageExecutionRequest {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputUri, "outputUri");
        Objects.requireNonNull(executionGrant, "executionGrant");
        Objects.requireNonNull(planVersion, "planVersion");
        Objects.requireNonNull(outputs, "outputs");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (attempt < 1 || stageKey == null || stageKey.isBlank()
            || schemaVersion == null || schemaVersion.isBlank()
            || profileCode == null || profileCode.isBlank()
            || profileVersion == null || profileVersion.isBlank()
            || processorVersion == null || processorVersion.isBlank() || planVersion.isBlank()) {
            throw new IllegalArgumentException("Stage execution request is incomplete.");
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Stage execution request inputs must not be empty.");
        }
        if (!ProcessingPlan.CURRENT_VERSION.equals(planVersion)) {
            throw new IllegalArgumentException("Only plan-v3 stage execution requests are supported.");
        }
    }
}
