package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.application.port.out.ArtifactValidator;
import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.ExecutionId;

import java.util.List;
import java.util.Objects;

public final class ArtifactRegistrationService implements ArtifactRegistration {
    private final ArtifactValidator validator;
    private final ArtifactRepository artifactRepository;

    public ArtifactRegistrationService(ArtifactValidator validator, ArtifactRepository artifactRepository) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository");
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
}
