package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetention;

import java.util.Optional;

/** Durable aggregate store for source-input retention and optimistic transitions. */
public interface TaskInputRetentionRepository {
    Optional<TaskInputRetention> find(TaskId taskId);
    boolean createIfAbsent(TaskInputRetention retention);
    boolean compareAndSet(TaskInputRetention expected, TaskInputRetention next);
}
