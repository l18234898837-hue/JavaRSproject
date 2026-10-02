package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetention;
import java.time.Instant;

public interface TaskInputRetentionUseCase {
    TaskInputRetention schedule(TaskId taskId, String workspaceKey, Instant expiresAt, AccessSubject operator);
    TaskInputRetention changePolicy(TaskId taskId, String workspaceKey, TaskInputRetention.PolicyChoice choice, AccessSubject operator);
    TaskInputRetention claim(TaskId taskId, long expectedVersion, Instant now);
}
