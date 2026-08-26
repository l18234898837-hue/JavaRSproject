package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.application.port.out.ArtifactValidator;
import com.htht.multisource.application.port.out.TaskArtifactStore;
import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.TaskStage;

import java.util.List;
import java.util.Objects;

public final class ArtifactRegistrationService implements ArtifactRegistration {
    private final ArtifactValidator validator;
    private final ArtifactRepository artifactRepository;
    private final TaskArtifactStore artifactStore;

    public ArtifactRegistrationService(ArtifactValidator validator, ArtifactRepository artifactRepository) {
        this(validator, artifactRepository, null);
    }

    public ArtifactRegistrationService(ArtifactValidator validator, ArtifactRepository artifactRepository,
                                       TaskArtifactStore artifactStore) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository");
        this.artifactStore = artifactStore;
    }

    @Override
    public void register(TaskId taskId, StageId stageId, StorageUri outputUri, ExecutionId executionId,
                         List<ArtifactCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        for (ArtifactCandidate candidate : candidates) {
            validator.validate(taskId, stageId, executionId, outputUri, candidate);
        }
        List<Artifact> existing = artifactRepository.findByTaskId(taskId);
        for (ArtifactCandidate candidate : candidates) {
            boolean registered = existing.stream().anyMatch(artifact -> artifact.stageId().equals(stageId)
                && artifact.uri().equals(candidate.uri()) && artifact.checksum().equals(candidate.checksum()));
            if (!registered) {
                artifactRepository.save(new Artifact(taskId, stageId, candidate.type(), candidate.uri(),
                    candidate.checksum(), candidate.sizeBytes()));
                existing = new java.util.ArrayList<>(existing);
                existing.add(new Artifact(taskId, stageId, candidate.type(), candidate.uri(),
                    candidate.checksum(), candidate.sizeBytes()));
            }
        }
    }

    @Override
    public void register(ProcessingTask task, TaskStage stage, ExecutionId executionId,
                         List<ArtifactCandidate> candidates) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(candidates, "candidates");
        if (artifactStore == null) {
            register(task.id(), stage.id(), task.outputUri(), executionId, candidates);
            return;
        }
        ProcessingStage declaredStage = task.plan().stages().stream()
            .filter(planStage -> planStage.stageKey().equals(stage.stageKey()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Task stage is not declared by the processing plan."));
        register(task.id(), stage, declaredStage, task.outputUri(), executionId, candidates);
    }

    public void register(TaskId taskId, TaskStage stage, ProcessingStage declaredStage, StorageUri outputUri,
                         ExecutionId executionId, List<ArtifactCandidate> candidates) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(declaredStage, "declaredStage");
        Objects.requireNonNull(outputUri, "outputUri");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(candidates, "candidates");
        if (artifactStore == null) {
            register(taskId, stage.id(), outputUri, executionId, candidates);
            return;
        }
        if (!stage.taskId().equals(taskId) || !stage.stageKey().equals(declaredStage.stageKey())) {
            throw new IllegalArgumentException("Task stage does not match the declared processing stage.");
        }
        validateDeclaredOutputs(outputUri, declaredStage.outputs(), candidates);
        for (ArtifactCandidate candidate : candidates) {
            validator.validate(taskId, stage.id(), executionId, outputUri, candidate);
        }
        List<Artifact> existing = artifactRepository.findByTaskId(taskId);
        for (ArtifactCandidate candidate : candidates) {
            StageOutput output = declaredOutput(outputUri, declaredStage.outputs(), candidate);
            Artifact artifact = artifactStore.materialize(taskId, stage.id(), stage.stageKey(), stage.attempt(),
                executionId, outputUri, candidate, output);
            boolean registered = existing.stream().anyMatch(current -> current.stageId().equals(stage.id())
                && current.uri().equals(candidate.uri()) && current.checksum().equals(candidate.checksum()));
            if (!registered) {
                artifactRepository.save(artifact);
                existing = new java.util.ArrayList<>(existing);
                existing.add(artifact);
            }
        }
    }

    private static void validateDeclaredOutputs(StorageUri outputUri, List<StageOutput> outputs,
                                                List<ArtifactCandidate> candidates) {
        if (outputs.size() != candidates.size()) {
            throw new IllegalArgumentException("Worker artifacts do not exactly match declared stage outputs.");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ArtifactCandidate candidate : candidates) {
            StageOutput output = declaredOutput(outputUri, outputs, candidate);
            if (!seen.add(output.artifactType() + "\\u0000" + output.relativePath())) {
                throw new IllegalArgumentException("Worker returned a duplicate declared artifact.");
            }
        }
        if (seen.size() != outputs.size()) {
            throw new IllegalArgumentException("Worker did not return every declared stage output.");
        }
    }

    private static StageOutput declaredOutput(StorageUri outputUri, List<StageOutput> outputs,
                                              ArtifactCandidate candidate) {
        String prefix = outputUri.value().endsWith("/") ? outputUri.value() : outputUri.value() + "/";
        return outputs.stream().filter(output -> output.artifactType().equals(candidate.type())
                && candidate.uri().value().equals(prefix + output.relativePath()))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Worker artifact is not declared by the stage."));
    }
}
