package com.htht.multisource.domain.model;

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
        }
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String stageKey : stagesByKey.keySet()) {
            visit(stageKey, stagesByKey, visited, visiting);
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
