package com.htht.multisource.domain.model;

import java.util.List;
import java.util.Objects;

public record ProcessingPlan(String version, List<ProcessingStage> stages) {

    public ProcessingPlan {
        Objects.requireNonNull(version, "version");
        stages = List.copyOf(stages);
        if (version.isBlank()) {
            throw new IllegalArgumentException("Processing plan version must not be blank.");
        }
        ProcessingPlanValidator.validate(stages);
    }
}
