package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskArtifactPurgePort;
import com.htht.multisource.application.port.out.TaskArtifactRetentionRepository;
import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.TaskArtifactPurgeUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.concurrent.Executor;

/** Coordinates the durable purge state transition with asynchronous filesystem work. */
public final class TaskArtifactPurgeService implements TaskArtifactPurgeUseCase {
    private final ProcessingTaskRepository tasks;
    private final TaskArtifactRetentionRepository retentions;
    private final TaskHistoryRepository history;
    private final GetTaskDetailUseCase details;
    private final TaskArtifactPurgePort purgePort;
    private final Executor executor;
    private final Supplier<Instant> clock;

    public TaskArtifactPurgeService(
        ProcessingTaskRepository tasks,
        TaskArtifactRetentionRepository retentions,
        TaskHistoryRepository history,
        GetTaskDetailUseCase details,
        TaskArtifactPurgePort purgePort,
        Executor executor,
        Supplier<Instant> clock
    ) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.retentions = Objects.requireNonNull(retentions, "retentions");
        this.history = Objects.requireNonNull(history, "history");
        this.details = Objects.requireNonNull(details, "details");
        this.purgePort = Objects.requireNonNull(purgePort, "purgePort");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TaskArtifactRetention request(TaskId taskId, AccessSubject subject, String confirmationTaskNo) {
        ProcessingTask task = tasks.findById(taskId)
            .filter(candidate -> candidate.creator().equals(subject))
            .orElseThrow(() -> new java.util.NoSuchElementException("Processing task not found."));
        requireTerminal(task.lifecycleStatus());
        String taskNo = history.findTaskNo(taskId, subject)
            .orElseThrow(() -> new java.util.NoSuchElementException("Processing task not found."));
        if (confirmationTaskNo == null || !taskNo.equals(confirmationTaskNo.trim())) {
            throw new IllegalArgumentException("Task number confirmation does not match.");
        }

        TaskArtifactRetention current = retentions.findOrCreate(taskId);
        TaskArtifactRetention next = current.beginPurge(subject, clock.get());
        if (next == current) {
            return current;
        }
        if (!retentions.compareAndSet(current, next)) {
            throw new IllegalStateException("Artifact retention changed concurrently.");
        }
        executor.execute(() -> completePurge(taskId, subject));
        return next;
    }

    private void completePurge(TaskId taskId, AccessSubject subject) {
        try {
            purgePort.purge(details.get(taskId, subject));
            transitionFromPurging(taskId, subject, true);
        } catch (RuntimeException exception) {
            transitionFromPurging(taskId, subject, false);
        }
    }

    private void transitionFromPurging(TaskId taskId, AccessSubject subject, boolean success) {
        retentions.find(taskId)
            .filter(retention -> retention.status() == TaskArtifactRetentionStatus.PURGING)
            .ifPresent(current -> {
                TaskArtifactRetention next = success
                    ? current.purgeSucceeded(subject, clock.get())
                    : current.purgeFailed(subject, clock.get(), "Task artifact deletion failed. Please retry.");
                retentions.compareAndSet(current, next);
            });
    }

    private static void requireTerminal(TaskLifecycleStatus lifecycleStatus) {
        if (lifecycleStatus != TaskLifecycleStatus.SUCCEEDED
            && lifecycleStatus != TaskLifecycleStatus.FAILED
            && lifecycleStatus != TaskLifecycleStatus.CANCELED) {
            throw new IllegalStateException("Task must be in a terminal state before artifacts can be purged.");
        }
    }
}
