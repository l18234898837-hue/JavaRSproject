package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.TaskId;

import java.util.Optional;

public interface DispatchNextStageUseCase {

    Optional<DispatchResult> dispatch(TaskId taskId);

    record DispatchResult(long stageId, String executionId) {
    }
}
