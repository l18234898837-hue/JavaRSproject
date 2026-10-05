package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ManagedInputFilePurgePort;
import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import com.htht.multisource.application.usecase.TaskInputPurgeUseCase;
import com.htht.multisource.application.usecase.TaskInputRetentionUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetention;
import com.htht.multisource.domain.model.TaskInputRetentionStatus;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Claims retention before release/delete so retries are safe and visible. */
public final class TaskInputPurgeService implements TaskInputPurgeUseCase {
    private final ProcessingTaskRepository tasks;
    private final TaskHistoryRepository history;
    private final TaskInputRetentionUseCase retentions;
    private final TaskInputReferenceRepository references;
    private final ManagedInputFileRepository files;
    private final ManagedInputFilePurgePort purgePort;
    private final Executor executor;
    private final Supplier<Instant> clock;

    public TaskInputPurgeService(ProcessingTaskRepository tasks, TaskHistoryRepository history,
                                 TaskInputRetentionUseCase retentions, TaskInputReferenceRepository references,
                                 ManagedInputFileRepository files, ManagedInputFilePurgePort purgePort,
                                 Executor executor, Supplier<Instant> clock) {
        this.tasks = Objects.requireNonNull(tasks); this.history = Objects.requireNonNull(history);
        this.retentions = Objects.requireNonNull(retentions); this.references = Objects.requireNonNull(references);
        this.files = Objects.requireNonNull(files); this.purgePort = Objects.requireNonNull(purgePort);
        this.executor = Objects.requireNonNull(executor); this.clock = Objects.requireNonNull(clock);
    }

    @Override public TaskInputRetention request(TaskId taskId, AccessSubject subject, String confirmationTaskNo) {
        return request(taskId, subject, confirmationTaskNo, "default");
    }

    @Override public TaskInputRetention request(TaskId taskId, AccessSubject subject, String confirmationTaskNo,
                                                String workspaceKey) {
        var task = tasks.findById(taskId).filter(value -> value.creator().equals(subject))
            .orElseThrow(() -> new java.util.NoSuchElementException("Processing task not found."));
        requireTerminal(task.lifecycleStatus());
        String taskNo = history.findTaskNo(taskId, subject).orElseThrow(() -> new java.util.NoSuchElementException("Processing task not found."));
        if (confirmationTaskNo == null || !taskNo.equals(confirmationTaskNo.trim()))
            throw new IllegalArgumentException("Task number confirmation does not match.");
        Instant now = clock.get();
        TaskInputRetention current = retentions.find(taskId).orElse(null);
        if (current != null && current.status() == TaskInputRetentionStatus.PURGED) return current;
        if (current != null && current.status() == TaskInputRetentionStatus.PURGING) return current;
        TaskInputRetention claimed = current != null && (current.status() == TaskInputRetentionStatus.RECYCLE_BIN
            || current.status() == TaskInputRetentionStatus.PURGE_FAILED)
            ? retentions.beginPurge(taskId, subject, now)
            : retentions.requestPurge(taskId, workspaceKey, subject, now);
        executor.execute(() -> complete(taskId, subject));
        return claimed;
    }

    private void complete(TaskId taskId, AccessSubject subject) {
        try {
            List<Long> managedIds = references.findActiveManagedFiles(taskId);
            if (managedIds.isEmpty()) {
                throw new IllegalStateException("No managed source inputs are registered; source cleanup was aborted.");
            }
            references.releaseForTask(taskId, subject.userId(), clock.get());
            for (Long managedId : managedIds) {
                if (managedId != null) files.findById(managedId).ifPresent(file -> {
                    ManagedInputFilePurgePort.Result result = purgePort.purge(file);
                    if (result == ManagedInputFilePurgePort.Result.PROTECTED) {
                        throw new IllegalStateException("Managed input is still referenced by another task.");
                    }
                });
            }
            retentions.complete(taskId, subject, clock.get());
        } catch (RuntimeException failure) {
            String reason = failure.getMessage() == null ? "Source input deletion failed. Please retry." : failure.getMessage();
            try { retentions.fail(taskId, subject, clock.get(), reason); } catch (RuntimeException ignored) { }
        }
    }

    private static void requireTerminal(TaskLifecycleStatus status) {
        if (status != TaskLifecycleStatus.SUCCEEDED && status != TaskLifecycleStatus.FAILED && status != TaskLifecycleStatus.CANCELED)
            throw new IllegalStateException("Task must be in a terminal state before source inputs can be purged.");
    }
}
