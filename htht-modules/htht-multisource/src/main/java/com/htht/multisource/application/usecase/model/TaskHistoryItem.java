package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.time.Instant;
import java.util.List;

public record TaskHistoryItem(TaskId taskId, String taskNo, String taskName, List<String> productCodes,
                              Instant completedAt, TaskLifecycleStatus lifecycleStatus, int artifactCount,
                              long artifactSizeBytes, TaskArtifactRetentionStatus retentionStatus) {
    public TaskHistoryItem { productCodes = List.copyOf(productCodes); }
}
