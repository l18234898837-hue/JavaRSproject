package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.application.port.out.TaskInputRetentionRepository;
import com.htht.multisource.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

@Tag("dev")
class TaskInputRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final AccessSubject OPERATOR = new AccessSubject(7);
    private static WorkspaceStoragePolicyRepository policies() { return key -> Optional.empty(); }

    @Test void usesTheRequestedWorkspacePolicy() {
        WorkspaceStoragePolicyRepository repository = new WorkspaceStoragePolicyRepository() {
            @Override public Optional<Policy> findByWorkspace(String workspaceKey) {
                return Optional.of(new Policy(30, 24, 48, false));
            }
        };
        TaskInputRetention retention = new TaskInputRetentionService(repository, () -> NOW)
            .schedule(TaskId.of(3), "workspace-a", null, OPERATOR);
        assertThat(retention.expiresAt()).isEqualTo(NOW.plusSeconds(30 * 86400L));
    }

    @Test void defaultPolicyChangeReschedulesExistingSchedule() {
        TaskInputRetentionService service = new TaskInputRetentionService(policies(), () -> NOW);
        service.schedule(TaskId.of(4), "default", NOW.plusSeconds(3600), OPERATOR);
        TaskInputRetention changed = service.changePolicy(TaskId.of(4), "default",
            TaskInputRetention.PolicyChoice.DEFAULT, OPERATOR);
        assertThat(changed.status()).isEqualTo(TaskInputRetentionStatus.SCHEDULED);
        assertThat(changed.expiresAt()).isEqualTo(NOW.plusSeconds(14 * 86400L));
    }

    @Test void policyChangeCannotOverwritePurgingState() {
        TaskInputRetentionService service = new TaskInputRetentionService(policies(), () -> NOW);
        TaskInputRetention scheduled = service.schedule(TaskId.of(5), "default", NOW, OPERATOR);
        service.claim(TaskId.of(5), scheduled.version(), NOW);

        assertThatThrownBy(() -> service.changePolicy(TaskId.of(5), "default",
            TaskInputRetention.PolicyChoice.DEFAULT, OPERATOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PURGING");
        assertThat(service.find(TaskId.of(5)).orElseThrow().status())
            .isEqualTo(TaskInputRetentionStatus.PURGING);
    }

    @Test void defaultAndCustomExpiryAreSupported() {
        assertThat(WorkspaceStoragePolicyRepository.Policy.defaults().autoCleanupEnabled()).isFalse();
        TaskInputRetentionService service = new TaskInputRetentionService(policies(), () -> NOW);
        TaskInputRetention defaulted = service.schedule(TaskId.of(1), "default", null, OPERATOR);
        assertThat(defaulted.expiresAt()).isEqualTo(NOW.plusSeconds(14 * 86400L));
        TaskInputRetention custom = new TaskInputRetentionService(policies(), () -> NOW)
            .schedule(TaskId.of(2), "default", NOW.plusSeconds(3600), OPERATOR);
        assertThat(custom.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test void staleClaimConflictsAndFailedPurgeCanRetry() {
        TaskInputRetentionService service = new TaskInputRetentionService(policies(), () -> NOW);
        TaskInputRetention scheduled = service.schedule(TaskId.of(1), "default", NOW, OPERATOR);
        assertThatThrownBy(() -> service.claim(TaskId.of(1), scheduled.version() - 1, NOW))
            .isInstanceOf(TaskInputRetentionService.OptimisticLockConflictException.class);
        TaskInputRetention purging = service.claim(TaskId.of(1), scheduled.version(), NOW);
        TaskInputRetention failed = purging.purgeFailed(OPERATOR, NOW, "temporary");
        assertThat(failed.claim(NOW).status()).isEqualTo(TaskInputRetentionStatus.PURGING);
    }

    @Test void permanentPinAndPurgedRejection() {
        TaskInputRetentionService service = new TaskInputRetentionService(policies(), () -> NOW);
        TaskInputRetention pinned = service.changePolicy(TaskId.of(1), "default", TaskInputRetention.PolicyChoice.PERMANENT, OPERATOR);
        assertThat(pinned.status()).isEqualTo(TaskInputRetentionStatus.AVAILABLE);
        assertThatThrownBy(() -> service.claim(TaskId.of(1), pinned.version(), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test void readsRetentionStateThroughTheDurableRepositoryAfterServiceReplacement() {
        Retentions repository = new Retentions();
        TaskInputRetentionService first = new TaskInputRetentionService(policies(), repository, () -> NOW);
        first.schedule(TaskId.of(9), "default", NOW.plusSeconds(3600), OPERATOR);

        TaskInputRetention persisted = new TaskInputRetentionService(policies(), repository, () -> NOW)
            .find(TaskId.of(9)).orElseThrow();

        assertThat(persisted.status()).isEqualTo(TaskInputRetentionStatus.SCHEDULED);
        assertThat(persisted.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    private static final class Retentions implements TaskInputRetentionRepository {
        private final java.util.Map<TaskId, TaskInputRetention> values = new java.util.HashMap<>();
        @Override public Optional<TaskInputRetention> find(TaskId id) { return Optional.ofNullable(values.get(id)); }
        @Override public boolean createIfAbsent(TaskInputRetention value) { if (values.containsKey(value.taskId())) return false; values.put(value.taskId(), value); return true; }
        @Override public boolean compareAndSet(TaskInputRetention expected, TaskInputRetention next) {
            if (!expected.equals(values.get(expected.taskId()))) return false; values.put(next.taskId(), next); return true;
        }
    }
}
