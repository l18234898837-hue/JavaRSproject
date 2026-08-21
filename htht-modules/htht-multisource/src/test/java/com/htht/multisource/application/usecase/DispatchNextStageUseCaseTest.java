package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.StageClaim;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageExecution;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class DispatchNextStageUseCaseTest {

    private static final TaskId TASK_ID = TaskId.of(101L);
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final Instant LEASE_EXPIRES_AT = Instant.parse("2026-08-21T09:05:00Z");

    @Test
    void claimsOnlyPendingStageWhoseDependenciesHaveSucceeded() {
        InMemoryStageRepository repository = new InMemoryStageRepository(List.of(
            pending(201L, "READ"), pending(202L, "CAL")
        ), Map.of("READ", List.of(), "CAL", List.of("READ")));

        Optional<StageClaim> first = repository.claimNextRunnable(TASK_ID, execution("exec-01"), NOW);
        Optional<StageClaim> blocked = repository.claimNextRunnable(TASK_ID, execution("exec-02"), NOW);
        repository.complete(new StageId(201L));
        Optional<StageClaim> second = repository.claimNextRunnable(TASK_ID, execution("exec-03"), NOW);

        assertThat(first).map(claim -> claim.stage().stageKey()).contains("READ");
        assertThat(blocked).isEmpty();
        assertThat(second).map(claim -> claim.stage().stageKey()).contains("CAL");
    }

    @Test
    void doesNotClaimStageAlreadyTransitionedToRunning() {
        InMemoryStageRepository repository = new InMemoryStageRepository(
            List.of(pending(201L, "READ")), Map.of("READ", List.of())
        );

        Optional<StageClaim> first = repository.claimNextRunnable(TASK_ID, execution("exec-01"), NOW);
        Optional<StageClaim> duplicate = repository.claimNextRunnable(TASK_ID, execution("exec-02"), NOW);

        assertThat(first).isPresent();
        assertThat(duplicate).isEmpty();
        assertThat(repository.findById(new StageId(201L))).get().extracting(TaskStage::status)
            .isEqualTo(StageExecutionStatus.RUNNING);
    }

    private static TaskStage pending(long stageId, String stageKey) {
        return new TaskStage(new StageId(stageId), TASK_ID, stageKey, StageExecutionStatus.PENDING, 1);
    }

    private static StageExecution execution(String executionId) {
        return new StageExecution(new ExecutionId(executionId), null, LEASE_EXPIRES_AT, null, null, NOW, null);
    }

    private static final class InMemoryStageRepository implements TaskStageRepository {
        private final Map<StageId, TaskStage> stages;
        private final Map<String, List<String>> dependencies;

        private InMemoryStageRepository(List<TaskStage> stages, Map<String, List<String>> dependencies) {
            this.stages = new LinkedHashMap<>();
            stages.forEach(stage -> this.stages.put(stage.id(), stage));
            this.dependencies = dependencies;
        }

        @Override
        public Optional<TaskStage> findById(StageId stageId) {
            return Optional.ofNullable(stages.get(stageId));
        }

        @Override
        public List<TaskStage> findByTaskId(TaskId taskId) {
            return stages.values().stream().filter(stage -> stage.taskId().equals(taskId)).toList();
        }

        @Override
        public void save(TaskStage stage) {
            stages.put(stage.id(), stage);
        }

        @Override
        public Optional<StageClaim> claimNextRunnable(TaskId taskId, StageExecution execution, Instant now) {
            return stages.values().stream()
                .filter(stage -> stage.taskId().equals(taskId) && stage.status() == StageExecutionStatus.PENDING)
                .filter(this::dependenciesSucceeded)
                .findFirst()
                .map(stage -> {
                    TaskStage claimed = stage.start(execution.executionId(), execution.workerId(), now, execution.leaseExpiresAt());
                    stages.put(claimed.id(), claimed);
                    return new StageClaim(claimed);
                });
        }

        private void complete(StageId stageId) {
            TaskStage stage = stages.get(stageId);
            stages.put(stageId, stage.succeed(stage.execution().executionId(), stage.attempt(), NOW));
        }

        private boolean dependenciesSucceeded(TaskStage stage) {
            return dependencies.getOrDefault(stage.stageKey(), List.of()).stream().allMatch(dependency ->
                stages.values().stream().anyMatch(candidate -> candidate.stageKey().equals(dependency)
                    && candidate.status() == StageExecutionStatus.SUCCEEDED)
            );
        }
    }
}
