package com.htht.config.multisource;

import com.htht.multisource.application.port.out.TaskArtifactAvailabilityProbe;
import com.htht.multisource.application.usecase.model.TaskArtifactRetentionScanCandidate;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Read-only local storage probe used solely by the historical retention initializer. */
public final class LocalTaskArtifactAvailabilityProbe implements TaskArtifactAvailabilityProbe {
    private final Path artifactRoot;

    public LocalTaskArtifactAvailabilityProbe(Path artifactRoot) {
        this.artifactRoot = Objects.requireNonNull(artifactRoot, "artifactRoot").toAbsolutePath().normalize();
    }

    @Override
    public TaskArtifactRetentionStatus probe(TaskArtifactRetentionScanCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        for (String storageKey : candidate.storageKeys()) {
            Path path = resolveStorageKey(storageKey);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
                return TaskArtifactRetentionStatus.MISSING;
            }
        }
        return TaskArtifactRetentionStatus.AVAILABLE;
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("/") || storageKey.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Artifact storage key is invalid.");
        }
        for (String segment : storageKey.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Artifact storage key is invalid.");
            }
        }
        Path path = artifactRoot.resolve(storageKey).normalize();
        if (!path.startsWith(artifactRoot)) {
            throw new IllegalArgumentException("Artifact storage key escapes the artifact root.");
        }
        return path;
    }
}
