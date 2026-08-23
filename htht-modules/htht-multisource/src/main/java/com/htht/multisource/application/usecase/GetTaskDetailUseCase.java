package com.htht.multisource.application.usecase;

import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;

public interface GetTaskDetailUseCase {

    TaskDetailView get(TaskId taskId, AccessSubject subject);
}
