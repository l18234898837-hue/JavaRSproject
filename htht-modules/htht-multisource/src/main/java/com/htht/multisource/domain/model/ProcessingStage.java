package com.htht.multisource.domain.model;

import java.util.List;
import java.util.Objects;

public record ProcessingStage(
    String stageKey,
    String stageCode,
    List<String> dependsOnStageKeys
) {
    public ProcessingStage {
        Objects.requireNonNull(stageKey, "stageKey");
        Objects.requireNonNull(stageCode, "stageCode");
        dependsOnStageKeys = List.copyOf(dependsOnStageKeys);
        if (stageKey.isBlank() || stageCode.isBlank()) {
            throw new IllegalArgumentException("Stage key and code must not be blank.");
        }
    }
}
