package com.htht.multisource.domain.model;

import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.application.port.out.model.StageOutput;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProcessingPlanValidator {

    private ProcessingPlanValidator() {
    }

    static void validate(List<ProcessingStage> stages) {
        Map<String, ProcessingStage> stagesByKey = new HashMap<>();
        for (ProcessingStage stage : stages) {
            if (stagesByKey.putIfAbsent(stage.stageKey(), stage) != null) {
                throw new IllegalArgumentException("Processing plan contains duplicate stage keys.");
            }
        }
        for (ProcessingStage stage : stages) {
            for (String dependencyKey : stage.dependsOnStageKeys()) {
                if (stage.stageKey().equals(dependencyKey) || !stagesByKey.containsKey(dependencyKey)) {
                    throw new IllegalArgumentException("Processing plan contains an invalid stage dependency.");
                }
            }
            validateStageBindings(stage, stagesByKey);
        }
        validateUniqueProducedArtifacts(stages);
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String stageKey : stagesByKey.keySet()) {
            visit(stageKey, stagesByKey, visited, visiting);
        }
    }

    private static void validateStageBindings(ProcessingStage stage, Map<String, ProcessingStage> stagesByKey) {
        Set<String> inputKeys = new HashSet<>();
        for (StageInput input : stage.inputs()) {
            if (!inputKeys.add(input.key())) {
                throw new IllegalArgumentException("Processing plan contains a duplicate input key.");
            }
            if (input.source() == StageInput.Source.UPSTREAM_ARTIFACT) {
                ProcessingStage producer = stage.dependsOnStageKeys().stream()
                    .map(stagesByKey::get)
                    .filter(candidate -> candidate.outputs().stream()
                        .anyMatch(output -> output.artifactType().equals(input.artifactType())))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Processing plan upstream artifact is absent from declared dependencies."
                    ));
                StageOutput output = producer.outputs().stream()
                    .filter(candidate -> candidate.artifactType().equals(input.artifactType()))
                    .findFirst()
                    .orElseThrow();
                if (output.retention() == StageOutput.Retention.FINAL) {
                    throw new IllegalArgumentException("Processing plan final artifact cannot be consumed downstream.");
                }
            }
        }
        Set<String> outputTypes = new HashSet<>();
        for (StageOutput output : stage.outputs()) {
            if (!outputTypes.add(output.artifactType())) {
                throw new IllegalArgumentException("Processing plan contains a duplicate output artifact type.");
            }
        }
    }

    private static void validateUniqueProducedArtifacts(List<ProcessingStage> stages) {
        Set<String> producedTypes = new HashSet<>();
        for (ProcessingStage stage : stages) {
            for (StageOutput output : stage.outputs()) {
                if (!producedTypes.add(output.artifactType())) {
                    throw new IllegalArgumentException("Processing plan contains a duplicate output artifact type.");
                }
            }
        }
    }

    private static void visit(
        String stageKey,
        Map<String, ProcessingStage> stagesByKey,
        Set<String> visited,
        Set<String> visiting
    ) {
        if (visited.contains(stageKey)) {
            return;
        }
        if (!visiting.add(stageKey)) {
            throw new IllegalArgumentException("Processing plan contains a cycle.");
        }
        for (String dependencyKey : stagesByKey.get(stageKey).dependsOnStageKeys()) {
            visit(dependencyKey, stagesByKey, visited, visiting);
        }
        visiting.remove(stageKey);
        visited.add(stageKey);
    }
}
