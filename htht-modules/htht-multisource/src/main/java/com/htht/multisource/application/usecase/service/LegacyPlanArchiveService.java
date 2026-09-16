package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.LegacyPlanArchiveRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Archives a historical task without deleting its snapshot, stages, events or
 * artifacts. The marker is deliberately separate so read APIs remain unchanged.
 */
public final class LegacyPlanArchiveService {
    private final ProcessingTaskRepository taskRepository;
    private final TaskStageRepository stageRepository;
    private final LegacyPlanArchiveRepository archiveRepository;
    private final InstantSupplier clock;

    public LegacyPlanArchiveService(ProcessingTaskRepository taskRepository,
                                    TaskStageRepository stageRepository,
                                    LegacyPlanArchiveRepository archiveRepository,
                                    InstantSupplier clock) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.archiveRepository = Objects.requireNonNull(archiveRepository, "archiveRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean archive(TaskId taskId, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Archive reason is required.");
        var task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Processing task not found: " + taskId.value()));
        String version = task.plan().version();
        if ("plan-v3".equals(version)) throw new IllegalStateException("plan-v3 tasks cannot be archived as legacy tasks.");
        if (archiveRepository.findByTaskId(taskId).isPresent()) return false;
        if (!isTerminal(task.lifecycleStatus())) {
            throw new IllegalStateException("Task must be terminal before archival: " + task.lifecycleStatus());
        }
        if (stageRepository.findByTaskId(taskId).stream()
            .anyMatch(stage -> stage.status() == StageExecutionStatus.RUNNING
                || stage.status() == StageExecutionStatus.RECOVERY_PENDING
                || stage.status() == StageExecutionStatus.RETRYING
                || stage.status() == StageExecutionStatus.PENDING)) {
            throw new IllegalStateException("Task has executable stages and cannot be archived.");
        }
        return archiveRepository.insertIfAbsent(taskId, version, reason, clock.now());
    }

    private static boolean isTerminal(TaskLifecycleStatus status) {
        return status == TaskLifecycleStatus.SUCCEEDED || status == TaskLifecycleStatus.FAILED
            || status == TaskLifecycleStatus.CANCELED;
    }

    @FunctionalInterface
    public interface InstantSupplier { Instant now(); }
}
