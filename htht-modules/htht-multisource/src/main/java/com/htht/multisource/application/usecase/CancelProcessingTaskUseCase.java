package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.AccessSubject;

public interface CancelProcessingTaskUseCase {

    void cancel(TaskId taskId, AccessSubject subject);
}
