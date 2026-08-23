package com.htht.multisource.domain.model;

import java.util.Objects;

public record Artifact(TaskId taskId, StageId stageId, String type, StorageUri uri, String checksum, long sizeBytes) {

    public Artifact(TaskId taskId, StageId stageId, String type, StorageUri uri, String checksum) {
        this(taskId, stageId, type, uri, checksum, 0L);
    }

    public Artifact {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(checksum, "checksum");
        if (type == null || type.isBlank() || checksum.isBlank() || sizeBytes < 0) {
            throw new IllegalArgumentException("Artifact type must not be blank.");
        }
    }
}
