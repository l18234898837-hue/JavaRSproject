package com.htht.config.multisource;

import com.htht.multisource.application.port.out.TaskArtifactPurgePort;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskStageView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.LongConsumer;

/** Deletes only derived files owned by one processing task. */
public final class LocalTaskArtifactPurgeService implements TaskArtifactPurgePort {
    private final Path workRoot;
    private final Path artifactRoot;
    private final LongConsumer cacheInvalidator;

    public LocalTaskArtifactPurgeService(Path workRoot, Path artifactRoot, LongConsumer cacheInvalidator) {
        this.workRoot = Objects.requireNonNull(workRoot, "workRoot").toAbsolutePath().normalize();
        this.artifactRoot = Objects.requireNonNull(artifactRoot, "artifactRoot").toAbsolutePath().normalize();
        this.cacheInvalidator = Objects.requireNonNull(cacheInvalidator, "cacheInvalidator");
        if (this.artifactRoot.startsWith(this.workRoot)) {
            throw new IllegalArgumentException("Artifact root must be outside the worker work root.");
        }
    }

    @Override
    public void purge(TaskDetailView detail) {
        Objects.requireNonNull(detail, "detail");
        long taskId = detail.id().value();
        validateArtifactStorageKeys(detail, taskId);

        Path taskArtifactDirectory = resolveInside(artifactRoot, Long.toString(taskId), "artifact directory");
        Path archiveDirectory = resolveInside(workRoot, "archives/" + taskId, "archive directory");
        var executionDirectories = detail.stages().stream()
            .map(TaskStageView::executionId)
            .filter(Objects::nonNull)
            .distinct()
            .map(executionId -> resolveExecutionDirectory(executionId))
            .toList();

        deleteTree(taskArtifactDirectory);
        for (Path executionDirectory : executionDirectories) {
            deleteTree(executionDirectory);
        }
        deleteTree(archiveDirectory);
        cacheInvalidator.accept(taskId);
    }

    private void validateArtifactStorageKeys(TaskDetailView detail, long taskId) {
        for (TaskArtifactView artifact : detail.artifacts()) {
            String storageKey = artifact.storageKey();
            if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("legacy/")) {
                continue;
            }
            if (storageKey.startsWith("/") || storageKey.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("Artifact storage key is invalid.");
            }
            Path relative = Path.of(storageKey).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")
                || !storageKey.startsWith(taskId + "/")) {
                throw new IllegalArgumentException("Artifact storage key is outside the task artifact directory.");
            }
            Path resolved = artifactRoot.resolve(relative).toAbsolutePath().normalize();
            if (!resolved.startsWith(artifactRoot)) {
                throw new IllegalArgumentException("Artifact storage key is outside the artifact root.");
            }
        }
    }

    private Path resolveExecutionDirectory(String executionId) {
        if (!executionId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Task execution id is invalid.");
        }
        return resolveInside(workRoot, executionId, "execution directory");
    }

    private static Path resolveInside(Path root, String child, String label) {
        Path resolved = root.resolve(child).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Task " + label + " escapes its configured root.");
        }
        return resolved;
    }

    private static void deleteTree(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new PurgeDeletionException(exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Task artifact deletion failed.", exception);
        } catch (PurgeDeletionException exception) {
            throw new IllegalStateException("Task artifact deletion failed.", exception.getCause());
        }
    }

    private static final class PurgeDeletionException extends RuntimeException {
        private PurgeDeletionException(IOException cause) {
            super(cause);
        }
    }
}
