package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetentionStatus;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.time.Instant;
import java.util.List;

public record TaskHistoryItem(TaskId taskId, String taskNo, String taskName, List<String> productCodes,
                              Instant completedAt, TaskLifecycleStatus lifecycleStatus, int artifactCount,
                              long artifactSizeBytes, TaskArtifactRetentionStatus retentionStatus,
                              TaskInputRetentionStatus sourceRetentionStatus) {
    public TaskHistoryItem {
        productCodes = List.copyOf(productCodes);
        if (sourceRetentionStatus == null) sourceRetentionStatus = TaskInputRetentionStatus.AVAILABLE;
    }

    public TaskHistoryItem(TaskId taskId, String taskNo, String taskName, List<String> productCodes,
                           Instant completedAt, TaskLifecycleStatus lifecycleStatus, int artifactCount,
                           long artifactSizeBytes, TaskArtifactRetentionStatus retentionStatus) {
        this(taskId, taskNo, taskName, productCodes, completedAt, lifecycleStatus, artifactCount,
            artifactSizeBytes, retentionStatus, TaskInputRetentionStatus.AVAILABLE);
    }

    public TaskArtifactRetentionStatus artifactRetentionStatus() { return retentionStatus; }
}
