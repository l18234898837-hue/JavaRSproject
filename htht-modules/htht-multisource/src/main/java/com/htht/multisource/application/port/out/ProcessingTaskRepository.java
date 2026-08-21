package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.util.Optional;

public interface ProcessingTaskRepository {

    Optional<ProcessingTask> findById(TaskId taskId);

    void save(ProcessingTask task);

    default void updateLifecycleStatus(TaskId taskId, TaskLifecycleStatus status) {
        // Legacy/in-memory adapters may not persist the derived summary yet.
    }
}
