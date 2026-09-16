package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.TaskId;

import java.util.Objects;

public record PlanRequest(
    TaskId taskId,
    ProductCandidate candidate,
    String parameterSnapshot,
    String requestedPlanVersion,
    AssetAccessGrant readGrant
) {
    public PlanRequest {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(requestedPlanVersion, "requestedPlanVersion");
        Objects.requireNonNull(readGrant, "readGrant");
        if (parameterSnapshot == null || parameterSnapshot.isBlank() || requestedPlanVersion.isBlank()) {
            throw new IllegalArgumentException("Parameter snapshot must not be blank.");
        }
        if (!"plan-v3".equals(requestedPlanVersion)) {
            throw new IllegalArgumentException("Only plan-v3 may be requested for new tasks.");
        }
    }
}
