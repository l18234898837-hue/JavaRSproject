package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.TaskId;

import java.util.List;
import java.util.Objects;

public record TaskArtifactRetentionScanCandidate(TaskId taskId, List<String> storageKeys) {
    public TaskArtifactRetentionScanCandidate {
        Objects.requireNonNull(taskId, "taskId");
        storageKeys = List.copyOf(Objects.requireNonNull(storageKeys, "storageKeys"));
    }
}
