package com.htht.multisource.domain.model;

import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.application.port.out.model.StageOutput;

import java.util.List;
import java.util.Objects;

public record ProcessingStage(
    String stageKey,
    String stageCode,
    List<String> dependsOnStageKeys,
    List<StageInput> inputs,
    List<StageOutput> outputs,
    String parameterSnapshot
) {
    public ProcessingStage(String stageKey, String stageCode, List<String> dependsOnStageKeys) {
        this(stageKey, stageCode, dependsOnStageKeys, List.of(), List.of(), "{}");
    }

    public ProcessingStage {
        Objects.requireNonNull(stageKey, "stageKey");
        Objects.requireNonNull(stageCode, "stageCode");
        Objects.requireNonNull(dependsOnStageKeys, "dependsOnStageKeys");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(parameterSnapshot, "parameterSnapshot");
        dependsOnStageKeys = List.copyOf(dependsOnStageKeys);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (stageKey.isBlank() || stageCode.isBlank() || parameterSnapshot.isBlank()) {
            throw new IllegalArgumentException("Stage key and code must not be blank.");
        }
        for (String dependencyKey : dependsOnStageKeys) {
            if (dependencyKey.isBlank()) {
                throw new IllegalArgumentException("Stage dependency key must not be blank.");
            }
        }
    }
}
