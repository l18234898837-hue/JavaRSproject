package com.htht.config.multisource;

import com.htht.multisource.application.port.out.TaskArtifactStore;
import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

public final class LocalTaskArtifactStore implements TaskArtifactStore {
    private final Path workRoot;
    private final Path artifactRoot;

    public LocalTaskArtifactStore(Path workRoot, Path artifactRoot) {
        this.workRoot = Objects.requireNonNull(workRoot, "workRoot").toAbsolutePath().normalize();
        this.artifactRoot = Objects.requireNonNull(artifactRoot, "artifactRoot").toAbsolutePath().normalize();
        if (this.artifactRoot.startsWith(this.workRoot)) {
            throw new IllegalArgumentException("Artifact root must be outside the worker work root.");
        }
    }

    @Override
    public Artifact materialize(TaskId taskId, StageId stageId, String stageKey, int attempt, ExecutionId executionId,
                                StorageUri outputUri, ArtifactCandidate candidate, StageOutput output) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(outputUri, "outputUri");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(output, "output");
        requireSegment(stageKey, "stageKey");
        if (attempt <= 0) {
            throw new IllegalArgumentException("Stage attempt must be positive.");
        }
        validateCandidate(outputUri, candidate, output);

        String storageKey = output.retention() == StageOutput.Retention.FINAL
            ? taskId.value() + "/output/" + output.relativePath()
            : taskId.value() + "/work/" + stageKey + "/" + attempt + "/" + output.relativePath();
        Path target = resolveStorageKey(storageKey);
        Path source = resolveExecutionOutput(executionId, output.relativePath());
        copyAndVerify(source, target, candidate);
        return new Artifact(taskId, stageId, candidate.type(), candidate.uri(), storageKey,
            candidate.checksum(), candidate.sizeBytes());
    }

    @Override
    public Path resolveForDownload(Artifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        return resolveStorageKey(artifact.storageKey());
    }

    private void validateCandidate(StorageUri outputUri, ArtifactCandidate candidate, StageOutput output) {
        if (!candidate.type().equals(output.artifactType())) {
            throw new IllegalArgumentException("Artifact type is not declared by the stage.");
        }
        String prefix = outputUri.value().endsWith("/") ? outputUri.value() : outputUri.value() + "/";
        if (!candidate.uri().value().equals(prefix + output.relativePath())) {
            throw new IllegalArgumentException("Artifact URI is not declared by the stage.");
        }
        if (!candidate.checksum().matches("sha256:[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Artifact checksum must be sha256:<64 hex characters>.");
        }
    }

    private Path resolveExecutionOutput(ExecutionId executionId, String relativePath) {
        Path outputRoot = workRoot.resolve(executionId.value()).resolve("output").normalize();
        Path source = outputRoot.resolve(relativePath).normalize();
        if (!source.startsWith(outputRoot) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new IllegalArgumentException("Artifact is not a readable regular file.");
        }
        return source;
    }

    private Path resolveStorageKey(String storageKey) {
        validateStorageKey(storageKey);
        Path path = artifactRoot.resolve(storageKey).normalize();
        if (!path.startsWith(artifactRoot)) {
            throw new IllegalArgumentException("Artifact storage key escapes the artifact root.");
        }
        return path;
    }

    private void copyAndVerify(Path source, Path target, ArtifactCandidate candidate) {
        Path temporary = null;
        try {
            if (Files.size(source) != candidate.sizeBytes() || !checksum(source).equalsIgnoreCase(candidate.checksum())) {
                throw new IllegalArgumentException("Artifact checksum does not match candidate metadata.");
            }
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".artifact-", "." + UUID.randomUUID());
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(temporary) != candidate.sizeBytes() || !checksum(temporary).equalsIgnoreCase(candidate.checksum())) {
                throw new IllegalArgumentException("Materialized artifact checksum does not match candidate metadata.");
            }
            moveAtomically(temporary, target);
        } catch (IOException exception) {
            throw new IllegalStateException("Artifact cannot be materialized.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A failed cleanup must not hide the materialization result.
                }
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String checksum(Path path) throws IOException {
        try {
            return "sha256:" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("/") || storageKey.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Artifact storage key is invalid.");
        }
        for (String segment : storageKey.split("/", -1)) {
            requireSegment(segment, "storageKey");
        }
    }

    private static void requireSegment(String segment, String field) {
        if (segment == null || segment.isBlank() || ".".equals(segment) || "..".equals(segment)
            || segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Artifact " + field + " is invalid.");
        }
    }
}
