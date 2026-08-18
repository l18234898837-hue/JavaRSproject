package com.htht.multisource.domain.model;

import java.util.Objects;

public record ProcessingTask(
    TaskId id,
    AccessSubject creator,
    TaskLifecycleStatus lifecycleStatus,
    QualityStatus qualityStatus,
    ProcessingPlan plan
) {
    public ProcessingTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        Objects.requireNonNull(qualityStatus, "qualityStatus");
    }
}
