package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.util.List;
import java.util.Objects;

public record TaskDetailView(
    TaskId id,
    long batchId,
    TaskLifecycleStatus lifecycleStatus,
    QualityStatus qualityStatus,
    ProcessingPlan plan,
    List<ProductCandidate> candidates,
    List<TaskStageView> stages,
    List<TaskArtifactView> artifacts
) {
    public TaskDetailView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        Objects.requireNonNull(qualityStatus, "qualityStatus");
        Objects.requireNonNull(plan, "plan");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
    }
}
