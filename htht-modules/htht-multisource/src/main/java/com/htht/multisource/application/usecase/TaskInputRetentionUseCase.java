package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetention;
import java.time.Instant;
import java.util.Optional;

public interface TaskInputRetentionUseCase {
    TaskInputRetention schedule(TaskId taskId, String workspaceKey, Instant expiresAt, AccessSubject operator);
    TaskInputRetention changePolicy(TaskId taskId, String workspaceKey, TaskInputRetention.PolicyChoice choice, AccessSubject operator);
    TaskInputRetention claim(TaskId taskId, long expectedVersion, Instant now);
    default TaskInputRetention recycle(TaskId taskId, String workspaceKey, AccessSubject operator, Instant now) {
        throw new UnsupportedOperationException("Input recycle-bin is not configured.");
    }
    default TaskInputRetention restore(TaskId taskId, AccessSubject operator, Instant now) {
        throw new UnsupportedOperationException("Input recycle-bin is not configured.");
    }
    default TaskInputRetention beginPurge(TaskId taskId, AccessSubject operator, Instant now) {
        throw new UnsupportedOperationException("Input purge is not configured.");
    }
    /** Atomically schedule an immediate purge and claim it for physical cleanup. */
    default TaskInputRetention requestPurge(TaskId taskId, String workspaceKey,
                                             AccessSubject operator, Instant now) {
        TaskInputRetention scheduled = schedule(taskId, workspaceKey, now, operator);
        return claim(taskId, scheduled.version(), now);
    }
    default Optional<TaskInputRetention> find(TaskId taskId) { return Optional.empty(); }
    default TaskInputRetention complete(TaskId taskId, AccessSubject operator, Instant now) {
        throw new UnsupportedOperationException("Input retention completion is not configured.");
    }
    default TaskInputRetention fail(TaskId taskId, AccessSubject operator, Instant now, String reason) {
        throw new UnsupportedOperationException("Input retention failure handling is not configured.");
    }
}
