package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskDispatcher;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.service.CancelProcessingTaskService;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.TaskStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class CancelProcessingTaskUseCaseTest {
    private static final TaskId TASK_ID = TaskId.of(101L);
    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

    @Test
    void ownerCancellationSkipsActiveStagesMarksTaskCancelledAndStopsWorker() {
        RecordingStages stages = new RecordingStages(List.of(
            pending(201L), pending(202L).start(new ExecutionId("exec-01"), "worker", NOW.minusSeconds(5), NOW.plusSeconds(60))
        ));
        RecordingTasks tasks = new RecordingTasks(ApplicationUseCaseTestFixtures.task(TASK_ID, new AccessSubject(7L)));
        RecordingDispatcher dispatcher = new RecordingDispatcher();

        new CancelProcessingTaskService(tasks, stages, dispatcher, () -> NOW).cancel(TASK_ID, new AccessSubject(7L));

        assertThat(tasks.statuses).containsExactly(TaskLifecycleStatus.CANCELING, TaskLifecycleStatus.CANCELED);
        assertThat(stages.stages).extracting(TaskStage::status).containsOnly(StageExecutionStatus.SKIPPED);
        assertThat(dispatcher.cancelled).containsExactly(new ExecutionId("exec-01"));
    }

    @Test
    void nonOwnerCannotCancelTask() {
        RecordingStages stages = new RecordingStages(List.of(pending(201L)));
        RecordingTasks tasks = new RecordingTasks(ApplicationUseCaseTestFixtures.task(TASK_ID, new AccessSubject(7L)));

        assertThatThrownBy(() -> new CancelProcessingTaskService(tasks, stages, new RecordingDispatcher(), () -> NOW)
            .cancel(TASK_ID, new AccessSubject(8L))).isInstanceOf(NoSuchElementException.class);
        assertThat(stages.stages).extracting(TaskStage::status).containsOnly(StageExecutionStatus.PENDING);
    }

    private static TaskStage pending(long stageId) {
        return new TaskStage(new StageId(stageId), TASK_ID, "STAGE-" + stageId, StageExecutionStatus.PENDING, 1);
    }

    private static final class RecordingTasks implements ProcessingTaskRepository {
        private final ProcessingTask task;
        private final List<TaskLifecycleStatus> statuses = new ArrayList<>();
        private RecordingTasks(ProcessingTask task) { this.task = task; }
        @Override public Optional<ProcessingTask> findById(TaskId taskId) { return Optional.of(task); }
        @Override public void save(ProcessingTask task) { throw new UnsupportedOperationException(); }
        @Override public void updateLifecycleStatus(TaskId taskId, TaskLifecycleStatus status) { statuses.add(status); }
    }

    private static final class RecordingStages implements TaskStageRepository {
        private List<TaskStage> stages;
        private RecordingStages(List<TaskStage> stages) { this.stages = new ArrayList<>(stages); }
        @Override public Optional<TaskStage> findById(StageId stageId) { return stages.stream().filter(s -> s.id().equals(stageId)).findFirst(); }
        @Override public List<TaskStage> findByTaskId(TaskId taskId) { return List.copyOf(stages); }
        @Override public void save(TaskStage stage) { stages = stages.stream().map(s -> s.id().equals(stage.id()) ? stage : s).toList(); }
    }

    private static final class RecordingDispatcher implements TaskDispatcher {
        private final List<ExecutionId> cancelled = new ArrayList<>();
        @Override public com.htht.multisource.application.port.out.model.DispatchReceipt dispatch(com.htht.multisource.application.port.out.model.StageExecutionRequest request) { throw new UnsupportedOperationException(); }
        @Override public void cancel(ExecutionId executionId) { cancelled.add(executionId); }
    }
}
