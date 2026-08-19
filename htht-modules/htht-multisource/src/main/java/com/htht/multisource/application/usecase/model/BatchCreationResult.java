package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.TaskId;

import java.util.List;
import java.util.Objects;

public record BatchCreationResult(ProcessingBatch batch, List<TaskId> taskIds) {
    public BatchCreationResult {
        Objects.requireNonNull(batch, "batch");
        taskIds = List.copyOf(Objects.requireNonNull(taskIds, "taskIds"));
        if (taskIds.isEmpty()) {
            throw new IllegalArgumentException("Batch must create at least one task.");
        }
    }
}
