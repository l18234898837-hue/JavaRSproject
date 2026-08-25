package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskStage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class StageInputResolver {

    public List<InputAsset> resolve(
        ProcessingTask task,
        ProcessingStage stage,
        List<TaskStage> taskStages,
        List<Artifact> artifacts
    ) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(taskStages, "taskStages");
        Objects.requireNonNull(artifacts, "artifacts");

        Map<StageId, String> stageKeysById = taskStages.stream()
            .filter(taskStage -> task.id().equals(taskStage.taskId()))
            .collect(Collectors.toMap(TaskStage::id, TaskStage::stageKey));

        return stage.inputs().stream()
            .flatMap(input -> resolveInput(task, stage, input, stageKeysById, artifacts).stream())
            .toList();
    }

    private List<InputAsset> resolveInput(
        ProcessingTask task,
        ProcessingStage stage,
        StageInput input,
        Map<StageId, String> stageKeysById,
        List<Artifact> artifacts
    ) {
        List<InputAsset> matches = switch (input.source()) {
            case RAW_INPUT -> task.candidates().get(0).files().stream()
                .filter(asset -> asset.role().equals(input.artifactType()))
                .toList();
            case UPSTREAM_ARTIFACT -> upstreamMatches(task, stage, input, stageKeysById, artifacts);
        };
        if (matches.isEmpty() && !input.required()) {
            return List.of();
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException(
                "Stage input '%s' must resolve exactly one asset but found %d.".formatted(input.key(), matches.size())
            );
        }
        return matches;
    }

    private List<InputAsset> upstreamMatches(
        ProcessingTask task,
        ProcessingStage stage,
        StageInput input,
        Map<StageId, String> stageKeysById,
        List<Artifact> artifacts
    ) {
        List<Artifact> matchingArtifacts = artifacts.stream()
            .filter(artifact -> task.id().equals(artifact.taskId()))
            .filter(artifact -> artifact.type().equals(input.artifactType()))
            .toList();

        for (Artifact artifact : matchingArtifacts) {
            String producerStageKey = stageKeysById.get(artifact.stageId());
            if (producerStageKey == null || !stage.dependsOnStageKeys().contains(producerStageKey)) {
                throw new IllegalArgumentException(
                    "Upstream artifact for stage input '%s' is not from a declared dependency.".formatted(input.key())
                );
            }
        }

        return matchingArtifacts.stream()
            .map(artifact -> new InputAsset(
                input.key(),
                0,
                new AssetReference("TASK_ARTIFACT", artifact.uri().value()),
                new com.htht.multisource.domain.model.StorageUri("rs://task-artifact/" + artifact.storageKey()),
                artifact.checksum(),
                input.required()
            ))
            .toList();
    }
}
