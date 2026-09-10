package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.domain.model.StorageUri;

import java.util.List;
import java.util.HashMap;
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
        Map<StageId, TaskStage> taskStagesById = taskStages.stream()
            .filter(taskStage -> task.id().equals(taskStage.taskId()))
            .collect(Collectors.toMap(TaskStage::id, taskStage -> taskStage));

        Map<String, Long> rawDeclarationCounts = stage.inputs().stream()
            .filter(input -> input.source() == StageInput.Source.RAW_INPUT)
            .collect(Collectors.groupingBy(StageInput::artifactType, Collectors.counting()));
        Map<String, Integer> rawOffsets = new HashMap<>();
        return stage.inputs().stream()
            .flatMap(input -> resolveInput(
                task,
                stage,
                input,
                stageKeysById,
                taskStagesById,
                artifacts,
                rawDeclarationCounts,
                rawOffsets
            ).stream())
            .toList();
    }

    private List<InputAsset> resolveInput(
        ProcessingTask task,
        ProcessingStage stage,
        StageInput input,
        Map<StageId, String> stageKeysById,
        Map<StageId, TaskStage> taskStagesById,
        List<Artifact> artifacts,
        Map<String, Long> rawDeclarationCounts,
        Map<String, Integer> rawOffsets
    ) {
        List<InputAsset> matches = switch (input.source()) {
            case RAW_INPUT -> rawMatches(task, input, rawDeclarationCounts, rawOffsets);
            case UPSTREAM_ARTIFACT -> upstreamMatches(task, stage, input, stageKeysById, taskStagesById, artifacts);
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

    private List<InputAsset> rawMatches(
        ProcessingTask task,
        StageInput input,
        Map<String, Long> declarationCounts,
        Map<String, Integer> offsets
    ) {
        List<InputAsset> matches = task.candidates().get(0).files().stream()
            .filter(asset -> asset.role().equals(input.artifactType()))
            .sorted(java.util.Comparator.comparingInt(InputAsset::ordinal))
            .toList();
        long declarationCount = declarationCounts.getOrDefault(input.artifactType(), 0L);
        if (declarationCount <= 1 || matches.isEmpty()) {
            return matches;
        }
        if (matches.size() != declarationCount) {
            throw new IllegalArgumentException(
                "Repeated stage input type '%s' declares %d assets but found %d."
                    .formatted(input.artifactType(), declarationCount, matches.size())
            );
        }
        int offset = offsets.merge(input.artifactType(), 1, Integer::sum) - 1;
        return List.of(matches.get(offset));
    }

    private List<InputAsset> upstreamMatches(
        ProcessingTask task,
        ProcessingStage stage,
        StageInput input,
        Map<StageId, String> stageKeysById,
        Map<StageId, TaskStage> taskStagesById,
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

        return matchingArtifacts.stream().map(artifact -> new InputAsset(
            input.key(), 0, new AssetReference("TASK_ARTIFACT", artifact.uri().value()),
            upstreamStorageUri(artifact, taskStagesById), artifact.checksum(), input.required()
        )).toList();
    }

    private static StorageUri upstreamStorageUri(Artifact artifact, Map<StageId, TaskStage> stages) {
        if (!artifact.storageKey().startsWith("legacy/")) {
            return new StorageUri("rs://task-artifact/" + artifact.storageKey());
        }
        TaskStage producer = java.util.Optional.ofNullable(stages.get(artifact.stageId()))
            .orElseThrow(() -> new IllegalArgumentException("Legacy artifact producer stage is unavailable."));
        String executionId = producer.execution() == null ? null : producer.execution().executionId().value();
        String uri = artifact.uri().value();
        String[] uriSegments = uri.startsWith("rs://output/")
            ? uri.substring("rs://output/".length()).split("/", 3) : new String[0];
        String relative = uriSegments.length == 3 ? uriSegments[2] : null;
        if (executionId == null || !executionId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
            || relative == null || relative.isBlank() || relative.indexOf('\\') >= 0
            || java.nio.file.Path.of(relative).isAbsolute() || java.nio.file.Path.of(relative).normalize().startsWith("..")) {
            throw new IllegalArgumentException("Legacy artifact cannot be resolved from its worker execution.");
        }
        return new StorageUri("rs://worker-output/" + executionId + "/" + relative);
    }
}
