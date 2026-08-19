package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.TaskId;

import java.util.Objects;

public record PlanRequest(
    TaskId taskId,
    ProductCandidate candidate,
    String parameterSnapshot,
    AssetAccessGrant readGrant
) {
    public PlanRequest {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(readGrant, "readGrant");
        if (parameterSnapshot == null || parameterSnapshot.isBlank()) {
            throw new IllegalArgumentException("Parameter snapshot must not be blank.");
        }
    }
}
