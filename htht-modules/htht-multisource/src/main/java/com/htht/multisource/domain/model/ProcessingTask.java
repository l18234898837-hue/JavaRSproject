package com.htht.multisource.domain.model;

import java.util.List;
import java.util.Objects;

public record ProcessingTask(
    TaskId id,
    String name,
    AccessSubject creator,
    TaskLifecycleStatus lifecycleStatus,
    QualityStatus qualityStatus,
    String parameterSnapshot,
    StorageUri outputUri,
    ProcessingPlan plan,
    ProcessingBatch batch,
    List<ProductCandidate> candidates
) {
    public ProcessingTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        Objects.requireNonNull(qualityStatus, "qualityStatus");
        Objects.requireNonNull(parameterSnapshot, "parameterSnapshot");
        Objects.requireNonNull(outputUri, "outputUri");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
        if (name.isBlank() || parameterSnapshot.isBlank() || candidates.isEmpty()) {
            throw new IllegalArgumentException("Processing task creation snapshots are invalid.");
        }
    }
}
