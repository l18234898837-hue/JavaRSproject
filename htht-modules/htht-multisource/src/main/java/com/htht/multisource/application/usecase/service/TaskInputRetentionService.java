package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.application.usecase.TaskInputRetentionUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetention;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Application service using immutable domain transitions and version-qualified claims. */
public final class TaskInputRetentionService implements TaskInputRetentionUseCase {
    private final WorkspaceStoragePolicyRepository policies;
    private final Supplier<Instant> clock;
    private final Map<TaskId, TaskInputRetention> values = new ConcurrentHashMap<>();

    public TaskInputRetentionService(WorkspaceStoragePolicyRepository policies, Supplier<Instant> clock) {
        this.policies = Objects.requireNonNull(policies, "policies");
        this.clock = Objects.requireNonNull(clock, "clock");
    }
    public TaskInputRetentionService(WorkspaceStoragePolicyRepository policies) { this(policies, Instant::now); }

    @Override
    public TaskInputRetention schedule(TaskId taskId, String workspaceKey, Instant expiresAt, AccessSubject operator) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(operator, "operator");
        String key = requireWorkspace(workspaceKey);
        Instant now = clock.get();
        Instant expiry = expiresAt == null ? now.plus(inputDays(key), java.time.temporal.ChronoUnit.DAYS) : expiresAt;
        TaskInputRetention next = values.compute(taskId, (id, current) -> {
            TaskInputRetention base = current == null ? TaskInputRetention.available(taskId, key, now) : current;
            return base.schedule(expiry, operator, now);
        });
        return next;
    }

    @Override
    public TaskInputRetention changePolicy(TaskId taskId, String workspaceKey, TaskInputRetention.PolicyChoice choice, AccessSubject operator) {
        Objects.requireNonNull(choice, "choice");
        String key = requireWorkspace(workspaceKey);
        Instant now = clock.get();
        TaskInputRetention next = values.compute(taskId, (id, current) -> {
            TaskInputRetention base = current == null ? TaskInputRetention.available(taskId, key, now) : current;
            return choice == TaskInputRetention.PolicyChoice.PERMANENT
                ? base.pin(operator, now)
                : base.schedule(now.plus(inputDays(key), java.time.temporal.ChronoUnit.DAYS), operator, now);
        });
        return next;
    }

    /** Convenience overload for callers receiving a policy choice from a text boundary. */
    public TaskInputRetention changePolicy(TaskId taskId, String workspaceKey, String choice, AccessSubject operator) {
        if (choice == null) throw new IllegalArgumentException("Policy choice is required.");
        return changePolicy(taskId, workspaceKey, TaskInputRetention.PolicyChoice.valueOf(choice.trim().toUpperCase()), operator);
    }

    @Override
    public TaskInputRetention claim(TaskId taskId, long expectedVersion, Instant now) {
        Objects.requireNonNull(taskId, "taskId"); Objects.requireNonNull(now, "now");
        TaskInputRetention current = values.get(taskId);
        if (current == null) throw new IllegalStateException("Input retention not found.");
        if (current.version() != expectedVersion) throw new OptimisticLockConflictException(taskId, expectedVersion, current.version());
        TaskInputRetention next = current.claim(now);
        if (!values.replace(taskId, current, next)) throw new OptimisticLockConflictException(taskId, expectedVersion, current.version());
        return next;
    }

    public Optional<TaskInputRetention> find(TaskId taskId) { return Optional.ofNullable(values.get(taskId)); }

    public TaskInputRetention requestPurge(TaskId taskId, String workspaceKey, AccessSubject operator, Instant now) {
        Objects.requireNonNull(now, "now");
        TaskInputRetention current = values.getOrDefault(taskId, TaskInputRetention.available(taskId, requireWorkspace(workspaceKey), now));
        if (current.status() == com.htht.multisource.domain.model.TaskInputRetentionStatus.PURGED) return current;
        if (current.status() == com.htht.multisource.domain.model.TaskInputRetentionStatus.PURGE_FAILED)
            return claim(taskId, current.version(), now);
        TaskInputRetention scheduled = current.schedule(now, operator, now);
        values.put(taskId, scheduled);
        return claim(taskId, scheduled.version(), now);
    }

    public TaskInputRetention completePurge(TaskId taskId, AccessSubject operator, Instant now) {
        TaskInputRetention current = values.get(taskId);
        if (current == null || current.status() != com.htht.multisource.domain.model.TaskInputRetentionStatus.PURGING) return current;
        TaskInputRetention next = current.purgeSucceeded(operator, now);
        values.replace(taskId, current, next);
        return next;
    }

    public TaskInputRetention failPurge(TaskId taskId, AccessSubject operator, Instant now, String reason) {
        TaskInputRetention current = values.get(taskId);
        if (current == null || current.status() != com.htht.multisource.domain.model.TaskInputRetentionStatus.PURGING) return current;
        TaskInputRetention next = current.purgeFailed(operator, now, reason);
        values.replace(taskId, current, next);
        return next;
    }

    private int inputDays(String workspaceKey) {
        // Until a multi-workspace request context is available, all tasks use the
        // default policy. Keep the workspace key on the aggregate for future
        // routing without applying workspace-specific policy prematurely.
        return policies.findDefault().orElse(WorkspaceStoragePolicyRepository.Policy.defaults()).inputRetentionDays();
    }
    private static String requireWorkspace(String key) { if (key == null || key.isBlank()) throw new IllegalArgumentException("Workspace key is required."); return key; }

    public static final class OptimisticLockConflictException extends IllegalStateException {
        public OptimisticLockConflictException(TaskId taskId, long expected, long actual) {
            super("Input retention changed concurrently for task " + taskId.value() + " (expected version " + expected + ", actual " + actual + ").");
        }
    }
}
