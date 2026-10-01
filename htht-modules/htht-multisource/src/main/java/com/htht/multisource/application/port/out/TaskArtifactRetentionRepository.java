package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskId;

import java.util.Optional;

public interface TaskArtifactRetentionRepository {
    Optional<TaskArtifactRetention> find(TaskId taskId);
    TaskArtifactRetention findOrCreate(TaskId taskId);
    default boolean createIfAbsent(TaskArtifactRetention retention) {
        throw new UnsupportedOperationException("Atomic retention creation is not supported by this repository.");
    }
    boolean compareAndSet(TaskArtifactRetention expected, TaskArtifactRetention next);
}
