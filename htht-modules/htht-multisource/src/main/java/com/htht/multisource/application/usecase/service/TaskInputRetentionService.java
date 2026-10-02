package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.application.port.out.TaskInputRetentionRepository;
import com.htht.multisource.application.usecase.TaskInputRetentionUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetention;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Application service using immutable domain transitions and version-qualified claims. */
public final class TaskInputRetentionService implements TaskInputRetentionUseCase {
    private final WorkspaceStoragePolicyRepository policies;
    private final TaskInputRetentionRepository repository;
    private final Supplier<Instant> clock;

    public TaskInputRetentionService(WorkspaceStoragePolicyRepository policies, TaskInputRetentionRepository repository,
                                     Supplier<Instant> clock) {
        this.policies = Objects.requireNonNull(policies, "policies");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }
    public TaskInputRetentionService(WorkspaceStoragePolicyRepository policies, TaskInputRetentionRepository repository) { this(policies, repository, Instant::now); }
    /** Test-only compatibility constructor. Production wiring must inject the persistent repository. */
    public TaskInputRetentionService(WorkspaceStoragePolicyRepository policies, Supplier<Instant> clock) {
        this(policies, new InMemoryRepository(), clock);
    }
    public TaskInputRetentionService(WorkspaceStoragePolicyRepository policies) { this(policies, new InMemoryRepository(), Instant::now); }

    @Override
    public TaskInputRetention schedule(TaskId taskId, String workspaceKey, Instant expiresAt, AccessSubject operator) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(operator, "operator");
        String key = requireWorkspace(workspaceKey);
        Instant now = clock.get();
        Instant expiry = expiresAt == null ? now.plus(inputDays(key), java.time.temporal.ChronoUnit.DAYS) : expiresAt;
        return transition(taskId, key, now, current -> current.schedule(expiry, operator, now));
    }

    @Override
    public TaskInputRetention changePolicy(TaskId taskId, String workspaceKey, TaskInputRetention.PolicyChoice choice, AccessSubject operator) {
        Objects.requireNonNull(choice, "choice");
        String key = requireWorkspace(workspaceKey);
        Instant now = clock.get();
        return transition(taskId, key, now, current -> choice == TaskInputRetention.PolicyChoice.PERMANENT
            ? current.pin(operator, now)
            : current.schedule(now.plus(inputDays(key), java.time.temporal.ChronoUnit.DAYS), operator, now));
    }

    /** Convenience overload for callers receiving a policy choice from a text boundary. */
    public TaskInputRetention changePolicy(TaskId taskId, String workspaceKey, String choice, AccessSubject operator) {
        if (choice == null) throw new IllegalArgumentException("Policy choice is required.");
        return changePolicy(taskId, workspaceKey, TaskInputRetention.PolicyChoice.valueOf(choice.trim().toUpperCase()), operator);
    }

    @Override
    public TaskInputRetention claim(TaskId taskId, long expectedVersion, Instant now) {
        Objects.requireNonNull(taskId, "taskId"); Objects.requireNonNull(now, "now");
        TaskInputRetention current = repository.find(taskId).orElseThrow(() -> new IllegalStateException("Input retention not found."));
        if (current.version() != expectedVersion) throw new OptimisticLockConflictException(taskId, expectedVersion, current.version());
        TaskInputRetention next = current.claim(now);
        if (!repository.compareAndSet(current, next)) throw conflict(taskId, expectedVersion);
        return next;
    }

    @Override public Optional<TaskInputRetention> find(TaskId taskId) { return repository.find(taskId); }

    @Override public TaskInputRetention complete(TaskId taskId, AccessSubject operator, Instant now) {
        return transition(taskId, operator, now, true, null);
    }

    @Override public TaskInputRetention fail(TaskId taskId, AccessSubject operator, Instant now, String reason) {
        return transition(taskId, operator, now, false, reason);
    }

    private TaskInputRetention transition(TaskId taskId, AccessSubject operator, Instant now, boolean success, String reason) {
        Objects.requireNonNull(taskId, "taskId"); Objects.requireNonNull(operator, "operator"); Objects.requireNonNull(now, "now");
        TaskInputRetention current = repository.find(taskId).orElseThrow(() -> new IllegalStateException("Input retention not found."));
        TaskInputRetention next = success ? current.purgeSucceeded(operator, now) : current.purgeFailed(operator, now, reason);
        if (!repository.compareAndSet(current, next)) throw conflict(taskId, current.version());
        return next;
    }

    public TaskInputRetention requestPurge(TaskId taskId, String workspaceKey, AccessSubject operator, Instant now) {
        Objects.requireNonNull(now, "now");
        String key = requireWorkspace(workspaceKey);
        TaskInputRetention current = currentOrCreate(taskId, key, now);
        if (current.status() == com.htht.multisource.domain.model.TaskInputRetentionStatus.PURGED) return current;
        if (current.status() == com.htht.multisource.domain.model.TaskInputRetentionStatus.PURGE_FAILED)
            return claim(taskId, current.version(), now);
        TaskInputRetention scheduled = current.schedule(now, operator, now);
        if (!repository.compareAndSet(current, scheduled)) throw conflict(taskId, current.version());
        return claim(taskId, scheduled.version(), now);
    }

    public TaskInputRetention completePurge(TaskId taskId, AccessSubject operator, Instant now) {
        TaskInputRetention current = repository.find(taskId).orElse(null);
        if (current == null || current.status() != com.htht.multisource.domain.model.TaskInputRetentionStatus.PURGING) return current;
        TaskInputRetention next = current.purgeSucceeded(operator, now);
        if (!repository.compareAndSet(current, next)) throw conflict(taskId, current.version());
        return next;
    }

    public TaskInputRetention failPurge(TaskId taskId, AccessSubject operator, Instant now, String reason) {
        TaskInputRetention current = repository.find(taskId).orElse(null);
        if (current == null || current.status() != com.htht.multisource.domain.model.TaskInputRetentionStatus.PURGING) return current;
        TaskInputRetention next = current.purgeFailed(operator, now, reason);
        if (!repository.compareAndSet(current, next)) throw conflict(taskId, current.version());
        return next;
    }

    private int inputDays(String workspaceKey) {
        return policies.findByWorkspace(workspaceKey).orElseGet(WorkspaceStoragePolicyRepository.Policy::defaults).inputRetentionDays();
    }
    private static String requireWorkspace(String key) { if (key == null || key.isBlank()) throw new IllegalArgumentException("Workspace key is required."); return key; }

    private TaskInputRetention transition(TaskId taskId, String workspaceKey, Instant now,
                                          java.util.function.Function<TaskInputRetention, TaskInputRetention> operation) {
        for (int attempt = 0; attempt < 2; attempt++) {
            TaskInputRetention current = currentOrCreate(taskId, workspaceKey, now);
            TaskInputRetention next = operation.apply(current);
            if (repository.compareAndSet(current, next)) return next;
        }
        throw conflict(taskId, -1L);
    }

    private TaskInputRetention currentOrCreate(TaskId taskId, String workspaceKey, Instant now) {
        Optional<TaskInputRetention> existing = repository.find(taskId);
        if (existing.isPresent()) return existing.get();
        repository.createIfAbsent(TaskInputRetention.available(taskId, workspaceKey, now));
        return repository.find(taskId).orElseThrow(() -> new IllegalStateException("Input retention could not be created."));
    }

    private OptimisticLockConflictException conflict(TaskId taskId, long expected) {
        long actual = repository.find(taskId).map(TaskInputRetention::version).orElse(-1L);
        return new OptimisticLockConflictException(taskId, expected, actual);
    }

    public static final class OptimisticLockConflictException extends IllegalStateException {
        public OptimisticLockConflictException(TaskId taskId, long expected, long actual) {
            super("Input retention changed concurrently for task " + taskId.value() + " (expected version " + expected + ", actual " + actual + ").");
        }
    }

    private static final class InMemoryRepository implements TaskInputRetentionRepository {
        private final java.util.Map<TaskId, TaskInputRetention> values = new ConcurrentHashMap<>();
        @Override public Optional<TaskInputRetention> find(TaskId taskId) { return Optional.ofNullable(values.get(taskId)); }
        @Override public boolean createIfAbsent(TaskInputRetention retention) { return values.putIfAbsent(retention.taskId(), retention) == null; }
        @Override public boolean compareAndSet(TaskInputRetention expected, TaskInputRetention next) {
            return values.replace(expected.taskId(), expected, next);
        }
    }
}
