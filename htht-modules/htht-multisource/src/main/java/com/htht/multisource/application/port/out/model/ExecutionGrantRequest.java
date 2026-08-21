package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;

import java.util.List;
import java.util.Objects;

public record ExecutionGrantRequest(
    TaskId taskId,
    StageId stageId,
    ExecutionId executionId,
    int attempt,
    List<InputAsset> inputs,
    StorageUri outputUri
) {
    public ExecutionGrantRequest {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputUri, "outputUri");
        inputs = List.copyOf(inputs);
        if (attempt < 1 || inputs.isEmpty()) {
            throw new IllegalArgumentException("Execution grant request is invalid.");
        }
    }
}
