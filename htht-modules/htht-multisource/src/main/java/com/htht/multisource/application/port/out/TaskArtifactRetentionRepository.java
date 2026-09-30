package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskId;

import java.util.Optional;

public interface TaskArtifactRetentionRepository {
    Optional<TaskArtifactRetention> find(TaskId taskId);
    TaskArtifactRetention findOrCreate(TaskId taskId);
    boolean compareAndSet(TaskArtifactRetention expected, TaskArtifactRetention next);
}
