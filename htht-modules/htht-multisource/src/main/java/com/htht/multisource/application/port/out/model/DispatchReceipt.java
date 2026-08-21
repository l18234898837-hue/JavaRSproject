package com.htht.multisource.application.port.out.model;

import java.util.List;

public record DispatchReceipt(String workerId, boolean succeeded, List<ArtifactCandidate> artifacts) {

    public DispatchReceipt {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("Worker id must not be blank.");
        }
        artifacts = List.copyOf(artifacts);
        if (!succeeded) {
            throw new IllegalArgumentException("Synchronous dispatch receipt must be terminally successful.");
        }
    }
}
