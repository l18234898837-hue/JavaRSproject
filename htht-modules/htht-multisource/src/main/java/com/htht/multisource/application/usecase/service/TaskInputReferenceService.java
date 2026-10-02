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
                if (!"SYS_FILE".equals(ref.sourceType())) continue;
                if (!ref.sourceId().matches("[1-9][0-9]*")) throw new IllegalArgumentException("Only positive SYS_FILE references are supported.");
                AssetStorage.ManagedInputFileMetadata metadata = assetStorage.managedInputFile(task.creator(), ref);
                long managedId = managedFiles.findOrRegister(workspaceKey, Long.parseLong(ref.sourceId()),
                    metadata.path(), metadata.checksum(), metadata.sizeBytes());
                if (!metadata.checksum().equals(input.checksum())) {
                    throw new IllegalArgumentException("Sys file checksum does not match the task snapshot.");
                }
                references.findOrRegister(task.id(), managedId, ref.sourceType(), ref.sourceId(), input.checksum());
            }
        }
    }
}
