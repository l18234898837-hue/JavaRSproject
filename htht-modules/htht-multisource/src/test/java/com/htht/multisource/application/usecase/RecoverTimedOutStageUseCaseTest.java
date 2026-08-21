package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.ExecutionClock;
import com.htht.multisource.application.port.out.ExecutionEventRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.application.usecase.service.RecoverTimedOutStageService;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.AccessSubject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class RecoverTimedOutStageUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-08-21T12:05:00Z");

    @Test
    void recoversEachExpiredRunningStageAndRecordsTimeoutEvent() {
        TaskStage expired = running(NOW.minusSeconds(1));
        RecordingStages stages = new RecordingStages(expired);
        RecordingEvents events = new RecordingEvents();

        RecordingTasks tasks = new RecordingTasks();
        int recovered = new RecoverTimedOutStageService(stages, events, tasks, () -> NOW).recoverExpired();

        assertThat(recovered).isEqualTo(1);
        assertThat(stages.stage.status()).isEqualTo(StageExecutionStatus.RECOVERY_PENDING);
        assertThat(events.events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(ExecutionEvent.Type.TIMED_OUT);
            assertThat(event.acceptance()).isEqualTo(ExecutionEvent.Acceptance.APPLIED);
        });
        assertThat(tasks.statuses).containsExactly(TaskLifecycleStatus.RECOVERY_PENDING);
    }

    private static TaskStage running(Instant leaseExpiry) {
        return new TaskStage(new StageId(201L), TaskId.of(101L), "READ", StageExecutionStatus.PENDING, 1)
            .start(new ExecutionId("exec-01"), "worker", NOW.minusSeconds(60), leaseExpiry);
    }

    private static final class RecordingStages implements TaskStageRepository {
        private TaskStage stage;
        private RecordingStages(TaskStage stage) { this.stage = stage; }
        @Override public Optional<TaskStage> findById(StageId stageId) { return Optional.of(stage); }
        @Override public List<TaskStage> findByTaskId(TaskId taskId) { return List.of(stage); }
        @Override public List<TaskStage> findExpiredRunning(Instant now) { return stage.execution().leaseExpiresAt().isBefore(now) ? List.of(stage) : List.of(); }
        @Override public void save(TaskStage stage) { this.stage = stage; }
    }

    private static final class RecordingEvents implements ExecutionEventRepository {
        private final List<ExecutionEvent> events = new ArrayList<>();
        @Override public boolean insertIfAbsent(ExecutionEvent event) { events.add(event); return true; }
    }

    private static final class RecordingTasks implements ProcessingTaskRepository {
        private final List<TaskLifecycleStatus> statuses = new ArrayList<>();
        @Override public Optional<com.htht.multisource.domain.model.ProcessingTask> findById(TaskId taskId) {
            return Optional.of(ApplicationUseCaseTestFixtures.task(taskId, new AccessSubject(7L)));
        }
        @Override public void save(com.htht.multisource.domain.model.ProcessingTask task) { throw new UnsupportedOperationException(); }
        @Override public void updateLifecycleStatus(TaskId taskId, TaskLifecycleStatus status) { statuses.add(status); }
    }
}
