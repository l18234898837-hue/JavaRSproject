package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingTask;

import java.util.Objects;

public final class TaskInputReferenceService {
    private final ManagedInputFileRepository managedFiles;
    private final TaskInputReferenceRepository references;
    private final AssetStorage assetStorage;

    public TaskInputReferenceService(ManagedInputFileRepository managedFiles,
                                     TaskInputReferenceRepository references,
                                     AssetStorage assetStorage) {
        this.managedFiles = Objects.requireNonNull(managedFiles, "managedFiles");
        this.references = Objects.requireNonNull(references, "references");
        this.assetStorage = Objects.requireNonNull(assetStorage, "assetStorage");
    }

    public void register(ProcessingTask task, String workspaceKey) {
        Objects.requireNonNull(task, "task");
        if (workspaceKey == null || workspaceKey.isBlank()) throw new IllegalArgumentException("Workspace key is required.");
        for (var candidate : task.candidates()) {
            for (InputAsset input : candidate.files()) {
                AssetReference ref = input.reference();
                AssetReference managedReference = managedReference(input);
                if (managedReference == null) continue;
                if (!managedReference.sourceId().matches("[1-9][0-9]*")) throw new IllegalArgumentException("Only positive SYS_FILE references are supported.");
                AssetStorage.ManagedInputFileMetadata metadata = assetStorage.managedInputFile(task.creator(), managedReference);
                if (!metadata.checksum().equals(input.checksum())) {
                    throw new IllegalArgumentException("Sys file checksum does not match the task snapshot.");
                }
                long managedId = managedFiles.findOrRegister(workspaceKey, Long.parseLong(managedReference.sourceId()),
                    metadata.path(), metadata.checksum(), metadata.sizeBytes());
                references.findOrRegister(task.id(), managedId, managedReference.sourceType(), managedReference.sourceId(), input.checksum());
            }
        }
    }

    /**
     * Uploads historically persisted a LOCAL_FILE reference while the storage
     * snapshot already contained the canonical sys-file identity. Reconcile
     * that representation here so retention tracks the same physical file.
     */
    private static AssetReference managedReference(InputAsset input) {
        AssetReference ref = input.reference();
        if ("SYS_FILE".equals(ref.sourceType())) return ref;
        String uri = input.storageUriSnapshot().value();
        String prefix = "rs://sys-file/";
        if (!uri.startsWith(prefix)) return null;
        String sysFileId = uri.substring(prefix.length());
        if (sysFileId.isBlank() || sysFileId.indexOf('/') >= 0) return null;
        return new AssetReference("SYS_FILE", sysFileId);
    }
}
