package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.TaskId;

public interface CreateProcessingTaskUseCase {

    TaskId create(CreateProcessingTaskCommand command);
}
