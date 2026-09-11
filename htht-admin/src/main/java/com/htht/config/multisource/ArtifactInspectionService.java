package com.htht.config.multisource;

import com.fasterxml.jackson.databind.JsonNode;
import com.htht.multisource.application.port.out.model.ArtifactInspectionRequest;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.infrastructure.worker.LocalProcessWorkerGateway;

import java.util.List;
import java.util.Objects;

/** Issues a short-lived read grant for one registered artifact and delegates bounded inspection to the worker. */
public final class ArtifactInspectionService {
    private final LocalProcessWorkerGateway workerGateway;
    private final CompositeLocalWorkerAssetResolver assetStorage;

    public ArtifactInspectionService(LocalProcessWorkerGateway workerGateway, CompositeLocalWorkerAssetResolver assetStorage) {
        this.workerGateway = Objects.requireNonNull(workerGateway, "workerGateway");
        this.assetStorage = Objects.requireNonNull(assetStorage, "assetStorage");
    }

    public JsonNode inspect(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact, String format) {
        InputAsset input = new InputAsset("ARTIFACT", 0,
            new AssetReference("TASK_ARTIFACT", artifact.type()), artifactUri(detail, subject, artifact),
            artifact.checksum(), true);
        return workerGateway.inspectArtifact(new ArtifactInspectionRequest(
            format, input, assetStorage.grantRead(subject, List.of(input))
        ));
    }

    private static StorageUri artifactUri(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact) {
        String storageKey = artifact.storageKey();
        if (storageKey != null && !storageKey.isBlank() && !storageKey.startsWith("legacy/") && safeRelative(storageKey)) {
            return new StorageUri("rs://task-artifact/" + storageKey);
        }
        String relative = outputRelative(subject, artifact);
        String executionId = detail.stages().stream().filter(stage -> stage.id().equals(artifact.stageId()))
            .map(stage -> stage.executionId()).filter(value -> value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
            .findFirst().orElseThrow(() -> new IllegalStateException("Registered artifact is unavailable for inspection."));
        return new StorageUri("rs://worker-output/" + executionId + "/" + relative);
    }

    private static String outputRelative(AccessSubject subject, TaskArtifactView artifact) {
        String prefix = "rs://output/" + subject.userId() + "/";
        String value = artifact.uri().value();
        if (!value.startsWith(prefix)) {
            throw new IllegalArgumentException("Artifact URI is not owned by the current user.");
        }
        String[] segments = value.substring(prefix.length()).split("/", 2);
        if (segments.length != 2 || !safeRelative(segments[1])) {
            throw new IllegalArgumentException("Artifact URI is invalid.");
        }
        return segments[1];
    }

    private static boolean safeRelative(String value) {
        return value != null && !value.isBlank() && value.indexOf('\\') < 0 && !java.nio.file.Path.of(value).isAbsolute()
            && !java.nio.file.Path.of(value).normalize().startsWith("..");
    }
}
