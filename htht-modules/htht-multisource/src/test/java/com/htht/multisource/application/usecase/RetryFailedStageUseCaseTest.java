package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.service.RetryFailedStageService;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.TaskStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class RetryFailedStageUseCaseTest {
    private static final TaskId TASK_ID = TaskId.of(101L);
    private static final AccessSubject OWNER = new AccessSubject(7L);

    @Test
    void ownerRetriesFailedStageBelowConfiguredAttemptLimit() {
        RecordingStages stages = new RecordingStages(failed(1));

        new RetryFailedStageService(new SingleTaskRepository(), stages, 3).retry(new StageId(201L), OWNER);

        assertThat(stages.stage.status()).isEqualTo(StageExecutionStatus.RETRYING);
        assertThat(stages.stage.attempt()).isEqualTo(2);
    }

    @Test
    void rejectsRetryWhenStageHasReachedTotalAttemptLimitOrCallerIsNotOwner() {
        assertThatThrownBy(() -> new RetryFailedStageService(new SingleTaskRepository(), new RecordingStages(failed(3)), 3)
            .retry(new StageId(201L), OWNER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RetryFailedStageService(new SingleTaskRepository(), new RecordingStages(failed(1)), 3)
            .retry(new StageId(201L), new AccessSubject(8L))).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void rejectsRetryForCancelledTask() {
        assertThatThrownBy(() -> new RetryFailedStageService(new SingleTaskRepository(TaskLifecycleStatus.CANCELED),
            new RecordingStages(failed(1)), 3).retry(new StageId(201L), OWNER))
            .isInstanceOf(IllegalStateException.class);
    }

    private static TaskStage failed(int attempt) {
        Instant started = Instant.parse("2026-08-21T12:00:00Z");
        return new TaskStage(new StageId(201L), TASK_ID, "READ", StageExecutionStatus.PENDING, attempt)
            .start(new ExecutionId("exec-" + attempt), "worker", started, started.plusSeconds(60))
            .fail(new ExecutionId("exec-" + attempt), attempt, "WORKER_FAILED", "failed", started.plusSeconds(1));
    }

    private static final class SingleTaskRepository implements ProcessingTaskRepository {
        private final TaskLifecycleStatus status;
        private SingleTaskRepository() { this(TaskLifecycleStatus.CREATED); }
        private SingleTaskRepository(TaskLifecycleStatus status) { this.status = status; }
        @Override public Optional<com.htht.multisource.domain.model.ProcessingTask> findById(TaskId taskId) {
            var template = ApplicationUseCaseTestFixtures.task(TASK_ID, OWNER);
            return Optional.of(new com.htht.multisource.domain.model.ProcessingTask(template.id(), template.name(),
                template.creator(), status, template.qualityStatus(), template.parameterSnapshot(), template.outputUri(),
                template.plan(), template.batch(), template.candidates()));
        }
        @Override public void save(com.htht.multisource.domain.model.ProcessingTask task) { throw new UnsupportedOperationException(); }
    }

    private static final class RecordingStages implements TaskStageRepository {
        private TaskStage stage;
        private RecordingStages(TaskStage stage) { this.stage = stage; }
        @Override public Optional<TaskStage> findById(StageId stageId) { return Optional.of(stage); }
        @Override public java.util.List<TaskStage> findByTaskId(TaskId taskId) { return java.util.List.of(stage); }
        @Override public void save(TaskStage stage) { this.stage = stage; }
    }
}
