package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;

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
    ExecutionGrant executionGrant
) {
    public StageExecutionRequest {
        if (attempt < 1 || stageKey == null || stageKey.isBlank()
            || schemaVersion == null || schemaVersion.isBlank()
            || profileCode == null || profileCode.isBlank()
            || profileVersion == null || profileVersion.isBlank()
            || processorVersion == null || processorVersion.isBlank()) {
            throw new IllegalArgumentException("Stage execution request is incomplete.");
        }
    }
}
