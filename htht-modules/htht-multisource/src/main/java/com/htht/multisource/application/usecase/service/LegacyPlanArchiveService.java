package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.LegacyPlanArchiveRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.CancelProcessingTaskUseCase;
import com.htht.multisource.domain.model.ProcessingPlan;
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
    private final CancelProcessingTaskUseCase cancelUseCase;

    public LegacyPlanArchiveService(ProcessingTaskRepository taskRepository,
                                    TaskStageRepository stageRepository,
                                    LegacyPlanArchiveRepository archiveRepository,
                                    InstantSupplier clock) {
        this(taskRepository, stageRepository, archiveRepository, clock, null);
    }

    public LegacyPlanArchiveService(ProcessingTaskRepository taskRepository,
                                    TaskStageRepository stageRepository,
                                    LegacyPlanArchiveRepository archiveRepository,
                                    InstantSupplier clock,
                                    CancelProcessingTaskUseCase cancelUseCase) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.archiveRepository = Objects.requireNonNull(archiveRepository, "archiveRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cancelUseCase = cancelUseCase;
    }

    public boolean archive(TaskId taskId, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Archive reason is required.");
        var task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Processing task not found: " + taskId.value()));
        String version = task.plan().version();
        if (ProcessingPlan.CURRENT_VERSION.equals(version)) {
            throw new IllegalStateException("plan-v3 tasks cannot be archived as legacy tasks.");
        }
        if (archiveRepository.findByTaskId(taskId).isPresent()) return false;
        if (!isTerminal(task.lifecycleStatus())) {
            if (cancelUseCase == null) {
                throw new IllegalStateException("Task must be terminal before archival: " + task.lifecycleStatus());
            }
            cancelUseCase.cancel(taskId, task.creator());
            task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Processing task disappeared during cancellation."));
            if (!isTerminal(task.lifecycleStatus())) {
                throw new IllegalStateException("Task cancellation did not reach a terminal state: " + task.lifecycleStatus());
            }
        }
        // A terminal task can retain downstream PENDING stages as an immutable
        // historical snapshot. It is not dispatchable once terminal, and the
        // maintenance controller separately rejects any fresh running lease.
        return archiveRepository.insertIfAbsent(taskId, version, reason, clock.now());
    }

    private static boolean isTerminal(TaskLifecycleStatus status) {
        return status == TaskLifecycleStatus.SUCCEEDED || status == TaskLifecycleStatus.FAILED
            || status == TaskLifecycleStatus.CANCELED;
    }

    @FunctionalInterface
    public interface InstantSupplier { Instant now(); }
}
