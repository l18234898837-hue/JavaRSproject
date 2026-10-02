package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetention;

/** Explicit, confirmed cleanup of the original source inputs for one task. */
public interface TaskInputPurgeUseCase {
    TaskInputRetention request(TaskId taskId, AccessSubject subject, String confirmationTaskNo);
}
