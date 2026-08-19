package com.htht.multisource.domain.model;

import java.util.List;
import java.util.Objects;

public record ProcessingPlan(String version, List<ProcessingStage> stages) {

    public ProcessingPlan {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(stages, "stages");
        stages = List.copyOf(stages);
        if (version.isBlank()) {
            throw new IllegalArgumentException("Processing plan version must not be blank.");
        }
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Processing plan stages must not be empty.");
        }
        ProcessingPlanValidator.validate(stages);
    }
}
