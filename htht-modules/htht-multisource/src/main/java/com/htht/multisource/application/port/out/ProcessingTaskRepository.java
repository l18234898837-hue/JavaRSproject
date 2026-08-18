package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.TaskId;

import java.util.Optional;

public interface ProcessingTaskRepository {

    Optional<ProcessingTask> findById(TaskId taskId);

    void save(ProcessingTask task);
}
