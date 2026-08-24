package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ExecutionClock;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskDispatcher;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.CancelProcessingTaskUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public class CancelProcessingTaskService implements CancelProcessingTaskUseCase {
    private final ProcessingTaskRepository taskRepository;
    private final TaskStageRepository stageRepository;
    private final TaskDispatcher dispatcher;
    private final ExecutionClock clock;

    public CancelProcessingTaskService(ProcessingTaskRepository taskRepository, TaskStageRepository stageRepository,
                                       TaskDispatcher dispatcher, ExecutionClock clock) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(TaskId taskId, AccessSubject subject) {
        var task = taskRepository.findById(taskId)
            .filter(value -> value.creator().equals(subject))
            .orElseThrow(() -> new NoSuchElementException("Processing task not found."));
        if (task.lifecycleStatus() == TaskLifecycleStatus.CANCELED) {
            return;
        }
        if (task.lifecycleStatus() == TaskLifecycleStatus.SUCCEEDED || task.lifecycleStatus() == TaskLifecycleStatus.FAILED) {
            throw new IllegalStateException("Terminal processing task cannot be canceled.");
        }

        taskRepository.updateLifecycleStatus(taskId, TaskLifecycleStatus.CANCELING);
        List<ExecutionId> executions = stageRepository.findByTaskId(taskId).stream()
            .filter(stage -> stage.status() == StageExecutionStatus.RUNNING)
            .map(stage -> stage.execution().executionId())
            .toList();
        stageRepository.findByTaskId(taskId).stream()
            .filter(stage -> stage.status() == StageExecutionStatus.PENDING
                || stage.status() == StageExecutionStatus.RETRYING
                || stage.status() == StageExecutionStatus.RECOVERY_PENDING
                || stage.status() == StageExecutionStatus.RUNNING)
            .map(stage -> stage.cancel(clock.now()))
            .forEach(stageRepository::save);
        taskRepository.updateLifecycleStatus(taskId, TaskLifecycleStatus.CANCELED);
        scheduleCancellation(executions);
    }

    private void scheduleCancellation(List<ExecutionId> executions) {
        Runnable cancellation = () -> executions.forEach(dispatcher::cancel);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cancellation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cancellation.run();
            }
        });
    }
}
