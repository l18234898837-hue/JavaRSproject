package com.htht.multisource.domain.model;

import java.util.Objects;

public record Artifact(TaskId taskId, StageId stageId, String type, StorageUri uri, String checksum) {

    public Artifact {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(uri, "uri");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Artifact type must not be blank.");
        }
    }
}
