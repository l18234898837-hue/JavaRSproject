package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.ExecutionEventRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.application.usecase.service.AcceptWorkerEventService;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class AcceptWorkerEventUseCaseTest {

    private static final TaskId TASK_ID = TaskId.of(101L);
    private static final StageId STAGE_ID = new StageId(201L);
    private static final Instant STARTED_AT = Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-08-21T10:01:00Z");

    @Test
    void appliesCurrentSuccessEventOnlyOnce() {
        InMemoryStageRepository stages = new InMemoryStageRepository(runningStage());
        InMemoryEventRepository events = new InMemoryEventRepository();
        AcceptWorkerEventUseCase useCase = new AcceptWorkerEventService(stages, events);
        ExecutionEvent success = event(new ExecutionId("exec-01"), ExecutionEvent.Type.SUCCEEDED, 1);

        assertThat(useCase.accept(success)).isEqualTo(AcceptWorkerEventUseCase.Result.APPLIED);
        assertThat(useCase.accept(success)).isEqualTo(AcceptWorkerEventUseCase.Result.DUPLICATE);
        assertThat(stages.stage.status()).isEqualTo(StageExecutionStatus.SUCCEEDED);
        assertThat(events.events.values()).singleElement().satisfies(event ->
            assertThat(event.acceptance()).isEqualTo(ExecutionEvent.Acceptance.APPLIED)
        );
    }

    @Test
    void registersTypedArtifactsBeforeMarkingStageSucceeded() {
        InMemoryStageRepository stages = new InMemoryStageRepository(runningStage());
        InMemoryEventRepository events = new InMemoryEventRepository();
        RecordingArtifactRegistration registration = new RecordingArtifactRegistration();
        AcceptWorkerEventUseCase useCase = new AcceptWorkerEventService(stages, events, registration,
            new SingleTaskRepository(new com.htht.multisource.domain.model.StorageUri("rs://output/7/task-01")));
        var artifact = new com.htht.multisource.application.port.out.model.ArtifactCandidate(
            new com.htht.multisource.domain.model.StorageUri("rs://output/7/task-01/result.tif"),
            "sha256:result", "OUTPUT", 42
        );

        assertThat(useCase.accept(event(new ExecutionId("exec-01"), ExecutionEvent.Type.SUCCEEDED, 1), List.of(artifact)))
            .isEqualTo(AcceptWorkerEventUseCase.Result.APPLIED);
        assertThat(registration.candidates).containsExactly(artifact);
        assertThat(stages.stage.status()).isEqualTo(StageExecutionStatus.SUCCEEDED);
    }

    @Test
    void recordsStaleEventAsIgnoredWithoutChangingCurrentStage() {
        InMemoryStageRepository stages = new InMemoryStageRepository(runningStage());
        InMemoryEventRepository events = new InMemoryEventRepository();
        AcceptWorkerEventUseCase useCase = new AcceptWorkerEventService(stages, events);

        assertThat(useCase.accept(event(new ExecutionId("old-execution"), ExecutionEvent.Type.FAILED, 2)))
            .isEqualTo(AcceptWorkerEventUseCase.Result.IGNORED);
        assertThat(stages.stage.status()).isEqualTo(StageExecutionStatus.RUNNING);
        assertThat(events.events.values()).singleElement().satisfies(event ->
            assertThat(event.acceptance()).isEqualTo(ExecutionEvent.Acceptance.IGNORED)
        );
    }

    @Test
    void appliesCurrentFailureWithWorkerReason() {
        InMemoryStageRepository stages = new InMemoryStageRepository(runningStage());
        AcceptWorkerEventUseCase useCase = new AcceptWorkerEventService(stages, new InMemoryEventRepository());

        assertThat(useCase.accept(event(new ExecutionId("exec-01"), ExecutionEvent.Type.FAILED, 1)))
            .isEqualTo(AcceptWorkerEventUseCase.Result.APPLIED);
        assertThat(stages.stage.status()).isEqualTo(StageExecutionStatus.FAILED);
        assertThat(stages.stage.execution().errorCode()).isEqualTo("WORKER_FAILED");
    }

    @Test
    void appliesStructuredWorkerFailureCodeAndMessage() {
        InMemoryStageRepository stages = new InMemoryStageRepository(runningStage());
        AcceptWorkerEventUseCase useCase = new AcceptWorkerEventService(stages, new InMemoryEventRepository());

        assertThat(useCase.accept(event(new ExecutionId("exec-01"), ExecutionEvent.Type.FAILED, 1,
            "INVALID_REQUEST", "L2A ROI bounds are invalid")))
            .isEqualTo(AcceptWorkerEventUseCase.Result.APPLIED);

        assertThat(stages.stage.execution().errorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(stages.stage.execution().errorMessage()).isEqualTo("L2A ROI bounds are invalid");
    }

    private static TaskStage runningStage() {
        return new TaskStage(STAGE_ID, TASK_ID, "READ", StageExecutionStatus.PENDING, 1)
            .start(new ExecutionId("exec-01"), "worker-local", STARTED_AT, STARTED_AT.plusSeconds(300));
    }

    private static ExecutionEvent event(ExecutionId executionId, ExecutionEvent.Type type, long sequence) {
        return event(executionId, type, sequence, "WORKER_FAILED", "processor failed");
    }

    private static ExecutionEvent event(ExecutionId executionId, ExecutionEvent.Type type, long sequence,
                                       String code, String message) {
        return new ExecutionEvent(TASK_ID, STAGE_ID, executionId, 1, sequence, type,
            "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}",
            FINISHED_AT, ExecutionEvent.Acceptance.APPLIED);
    }

    private static final class InMemoryStageRepository implements TaskStageRepository {
        private TaskStage stage;

        private InMemoryStageRepository(TaskStage stage) {
            this.stage = stage;
        }

        @Override
        public Optional<TaskStage> findById(StageId stageId) {
            return stage.id().equals(stageId) ? Optional.of(stage) : Optional.empty();
        }

        @Override
        public java.util.List<TaskStage> findByTaskId(TaskId taskId) {
            return stage.taskId().equals(taskId) ? java.util.List.of(stage) : java.util.List.of();
        }

        @Override
        public void save(TaskStage stage) {
            this.stage = stage;
        }
    }

    private static final class InMemoryEventRepository implements ExecutionEventRepository {
        private final Map<String, ExecutionEvent> events = new HashMap<>();

        @Override
        public boolean insertIfAbsent(ExecutionEvent event) {
            return events.putIfAbsent(event.stageId().value() + ":" + event.attempt() + ":" + event.eventSequence(), event) == null;
        }
    }

    private static final class SingleTaskRepository implements ProcessingTaskRepository {
        private final com.htht.multisource.domain.model.StorageUri outputUri;

        private SingleTaskRepository(com.htht.multisource.domain.model.StorageUri outputUri) {
            this.outputUri = outputUri;
        }

        @Override
        public Optional<com.htht.multisource.domain.model.ProcessingTask> findById(TaskId taskId) {
            var template = ApplicationUseCaseTestFixtures.task(taskId,
                new com.htht.multisource.domain.model.AccessSubject(7L));
            return Optional.of(new com.htht.multisource.domain.model.ProcessingTask(
                template.id(), template.name(), template.creator(), template.lifecycleStatus(), template.qualityStatus(),
                template.parameterSnapshot(), outputUri, template.plan(), template.batch(), template.candidates()
            ));
        }

        @Override
        public void save(com.htht.multisource.domain.model.ProcessingTask task) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingArtifactRegistration
        implements com.htht.multisource.application.usecase.service.ArtifactRegistration {
        private List<com.htht.multisource.application.port.out.model.ArtifactCandidate> candidates = List.of();

        @Override
        public void register(TaskId taskId, StageId stageId, com.htht.multisource.domain.model.StorageUri outputUri,
                             ExecutionId executionId,
                             List<com.htht.multisource.application.port.out.model.ArtifactCandidate> candidates) {
            this.candidates = candidates;
        }
    }
}
