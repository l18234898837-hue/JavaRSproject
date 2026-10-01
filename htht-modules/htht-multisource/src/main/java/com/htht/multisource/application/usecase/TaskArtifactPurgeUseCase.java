package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskId;

public interface TaskArtifactPurgeUseCase {

    TaskArtifactRetention request(TaskId taskId, AccessSubject subject, String confirmationTaskNo);
}
