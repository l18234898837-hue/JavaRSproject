package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.StorageUri;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rejects a Worker success receipt that does not fulfil the outputs declared
 * by its immutable processing-plan snapshot.
 */
public final class StageOutputContractValidator {

    public void validate(ProcessingStage stage, StorageUri outputUri, List<ArtifactCandidate> artifacts) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(outputUri, "outputUri");
        Objects.requireNonNull(artifacts, "artifacts");
        if (stage.outputs().isEmpty()) {
            return;
        }

        String prefix = outputUri.value().endsWith("/") ? outputUri.value() : outputUri.value() + "/";
        Set<OutputKey> expected = new HashSet<>();
        stage.outputs().forEach(output -> {
            OutputKey value = new OutputKey(output.artifactType(), prefix + output.relativePath());
            if (!expected.add(value)) {
                throw new IllegalStateException("Processing stage declares duplicate output artifacts.");
            }
        });
        Set<OutputKey> actual = new HashSet<>();
        for (ArtifactCandidate artifact : artifacts) {
            OutputKey value = new OutputKey(artifact.type(), artifact.uri().value());
            if (!actual.add(value)) {
                throw new IllegalArgumentException("Worker returned duplicate output artifacts.");
            }
        }
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("Worker output artifacts do not match the processing stage contract.");
        }
    }

    private record OutputKey(String type, String uri) {
    }
}
