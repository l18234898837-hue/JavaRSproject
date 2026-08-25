package com.htht.multisource.domain.model;

import java.util.Objects;

public record Artifact(TaskId taskId, StageId stageId, String type, StorageUri uri, String storageKey, String checksum,
                       long sizeBytes) {

    public Artifact(TaskId taskId, StageId stageId, String type, StorageUri uri, String checksum) {
        this(taskId, stageId, type, uri, legacyStorageKey(taskId, stageId), checksum, 0L);
    }

    public Artifact(TaskId taskId, StageId stageId, String type, StorageUri uri, String checksum, long sizeBytes) {
        this(taskId, stageId, type, uri, legacyStorageKey(taskId, stageId), checksum, sizeBytes);
    }

    public Artifact {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(checksum, "checksum");
        if (type == null || type.isBlank() || storageKey.isBlank() || checksum.isBlank() || sizeBytes < 0) {
            throw new IllegalArgumentException("Artifact type must not be blank.");
        }
        for (String segment : storageKey.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment) || segment.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("Artifact storage key is invalid.");
            }
        }
    }

    private static String legacyStorageKey(TaskId taskId, StageId stageId) {
        return "legacy/" + Objects.requireNonNull(taskId, "taskId").value() + "/" +
            Objects.requireNonNull(stageId, "stageId").value();
    }
}
