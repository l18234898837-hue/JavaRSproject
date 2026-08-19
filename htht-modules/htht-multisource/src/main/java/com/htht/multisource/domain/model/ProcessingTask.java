package com.htht.multisource.domain.model;

import java.util.List;
import java.util.Objects;

public record ProcessingTask(
    TaskId id,
    AccessSubject creator,
    TaskLifecycleStatus lifecycleStatus,
    QualityStatus qualityStatus,
    ProcessingPlan plan,
    ProcessingBatch batch,
    List<ProductCandidate> candidates
) {
    public ProcessingTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        Objects.requireNonNull(qualityStatus, "qualityStatus");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Processing task candidates must not be empty.");
        }
    }
}
