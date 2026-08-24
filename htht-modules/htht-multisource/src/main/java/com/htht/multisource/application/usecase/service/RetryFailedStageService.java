package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.RetryFailedStageUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;

public class RetryFailedStageService implements RetryFailedStageUseCase {
    private final ProcessingTaskRepository taskRepository;
    private final TaskStageRepository stageRepository;
    private final int maxAttempts;

    public RetryFailedStageService(ProcessingTaskRepository taskRepository, TaskStageRepository stageRepository,
                                   int maxAttempts) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Maximum attempts must be positive.");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(StageId stageId, AccessSubject subject) {
        var stage = stageRepository.findById(stageId)
            .orElseThrow(() -> new NoSuchElementException("Processing stage not found."));
        var task = taskRepository.findById(stage.taskId())
            .filter(value -> value.creator().equals(subject))
            .orElseThrow(() -> new NoSuchElementException("Processing stage not found."));
        if (task.lifecycleStatus() == TaskLifecycleStatus.CANCELING
            || task.lifecycleStatus() == TaskLifecycleStatus.CANCELED
            || task.lifecycleStatus() == TaskLifecycleStatus.SUCCEEDED
            || task.lifecycleStatus() == TaskLifecycleStatus.FAILED) {
            throw new IllegalStateException("Processing task cannot retry a stage in its current lifecycle state.");
        }
        if (stage.attempt() >= maxAttempts) {
            throw new IllegalStateException("Processing stage reached the maximum attempt limit.");
        }
        stageRepository.save(stage.retry());
    }
}
