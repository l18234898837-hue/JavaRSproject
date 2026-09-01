package com.htht.multisource.infrastructure.worker;

import com.htht.multisource.application.port.out.ArtifactValidator;
import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class LocalWorkerArtifactValidator implements ArtifactValidator {
    private final Path workRoot;

    public LocalWorkerArtifactValidator(Path workRoot) {
        this.workRoot = Objects.requireNonNull(workRoot, "workRoot").toAbsolutePath().normalize();
    }

    @Override
    public void validate(TaskId taskId, StageId stageId, ExecutionId executionId, StorageUri outputUri,
                         ArtifactCandidate candidate) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(outputUri, "outputUri");
        Objects.requireNonNull(candidate, "candidate");

        String prefix = outputUri.value().endsWith("/") ? outputUri.value() : outputUri.value() + "/";
        if (!candidate.uri().value().startsWith(prefix)) {
            throw new IllegalArgumentException("Artifact URI is outside task output URI.");
        }
        String relative = candidate.uri().value().substring(prefix.length());
        if (relative.isBlank() || relative.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Artifact path is invalid.");
        }

        Path outputRoot = workRoot.resolve(executionId.value()).resolve("output").normalize();
        Path artifactPath = outputRoot.resolve(relative).normalize();
        if (!artifactPath.startsWith(outputRoot) || Path.of(relative).isAbsolute()) {
            throw new IllegalArgumentException("Artifact path escapes execution output directory.");
        }
        try {
            if (!Files.isRegularFile(artifactPath) || !Files.isReadable(artifactPath)) {
                throw new IllegalArgumentException("Artifact is not a readable regular file.");
            }
            if (Files.size(artifactPath) != candidate.sizeBytes()) {
                throw new IllegalArgumentException("Artifact size does not match candidate metadata.");
            }
            if (!candidate.checksum().matches("sha256:[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Artifact checksum must be sha256:<64 hex characters>.");
            }
            String actual = "sha256:" + checksum(artifactPath);
            if (!actual.equalsIgnoreCase(candidate.checksum())) {
                throw new IllegalArgumentException("Artifact checksum does not match candidate metadata.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Artifact cannot be read.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static String checksum(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream source = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int count; (count = source.read(buffer)) >= 0;) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
