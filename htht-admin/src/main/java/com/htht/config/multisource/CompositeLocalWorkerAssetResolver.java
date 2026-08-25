package com.htht.config.multisource;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrantRequest;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.worker.LocalWorkerAssetResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Resolves raw sys-file and promoted task-artifact inputs under one execution grant. */
public final class CompositeLocalWorkerAssetResolver implements AssetStorage, LocalWorkerAssetResolver {
    private final SysFileAssetStorageAdapter sysFiles;
    private final Path artifactRoot;
    private final ConcurrentHashMap<String, GrantScope> grants = new ConcurrentHashMap<>();

    public CompositeLocalWorkerAssetResolver(SysFileAssetStorageAdapter sysFiles, Path artifactRoot) {
        this.sysFiles = sysFiles;
        this.artifactRoot = artifactRoot.toAbsolutePath().normalize();
    }

    @Override
    public Path resolve(String grantReference, StorageUri storageUri) {
        GrantScope scope = grants.get(grantReference);
        if (scope == null || !scope.expiresAt().isAfter(Instant.now())
            || !scope.storageUris().contains(storageUri.value())) {
            throw new IllegalArgumentException("Asset is not authorized by the grant.");
        }
        if (storageUri.value().startsWith("rs://sys-file/")) {
            return sysFiles.resolve(grantReference, storageUri);
        }
        if (!storageUri.value().startsWith("rs://task-artifact/")) {
            throw new IllegalArgumentException("Unsupported asset storage URI.");
        }
        String key = storageUri.value().substring("rs://task-artifact/".length());
        Path path = artifactRoot.resolve(key).normalize();
        if (!path.startsWith(artifactRoot) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Task artifact is unavailable.");
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Task artifact cannot be read.", exception);
        }
    }

    @Override
    public ExecutionGrant grant(ExecutionGrantRequest request) {
        String reference = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(300);
        Set<String> uris = request.inputs().stream().map(input -> input.storageUriSnapshot().value())
            .collect(Collectors.toUnmodifiableSet());
        grants.put(reference, new GrantScope(expiresAt, uris));
        sysFiles.authorize(reference, expiresAt, uris.stream().filter(uri -> uri.startsWith("rs://sys-file/")).collect(Collectors.toUnmodifiableSet()));
        return new ExecutionGrant(reference, expiresAt, request.taskId(), request.stageId(), request.executionId(), request.attempt());
    }

    @Override public AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs) {
        String ref = UUID.randomUUID().toString();
        Instant expires = Instant.now().plusSeconds(300);
        grants.put(ref, new GrantScope(expires, inputs.stream().map(i -> i.storageUriSnapshot().value()).collect(Collectors.toUnmodifiableSet())));
        return new AssetAccessGrant(ref, expires);
    }
    @Override public ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri) {
        throw new UnsupportedOperationException("Execution grants must bind a stage execution.");
    }
    @Override public com.htht.multisource.application.port.out.model.AssetMetadata resolve(AccessSubject subject, AssetReference reference) {
        return sysFiles.resolve(subject, reference);
    }

    private record GrantScope(Instant expiresAt, Set<String> storageUris) { }
}
