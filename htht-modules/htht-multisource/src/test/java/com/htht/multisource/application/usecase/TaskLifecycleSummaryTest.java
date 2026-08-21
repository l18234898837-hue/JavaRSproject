package com.htht.multisource.application.usecase;

import com.htht.multisource.application.usecase.service.TaskLifecycleSummaryService;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.domain.model.ExecutionId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TaskLifecycleSummaryTest {

    private static final TaskId TASK_ID = TaskId.of(101L);

    private final TaskLifecycleSummaryService service = new TaskLifecycleSummaryService();

    @Test
    void allSucceededStagesProduceSucceededTask() {
        assertThat(service.summarize(TaskLifecycleStatus.RUNNING, List.of(stage("READ", StageExecutionStatus.SUCCEEDED))))
            .isEqualTo(TaskLifecycleStatus.SUCCEEDED);
    }

    @Test
    void failedStageProducesFailedTask() {
        assertThat(service.summarize(TaskLifecycleStatus.RUNNING, List.of(stage("READ", StageExecutionStatus.FAILED))))
            .isEqualTo(TaskLifecycleStatus.FAILED);
    }

    @Test
    void activeStageProducesRunningTask() {
        assertThat(service.summarize(TaskLifecycleStatus.CREATED, List.of(stage("READ", StageExecutionStatus.RUNNING))))
            .isEqualTo(TaskLifecycleStatus.RUNNING);
    }

    private static TaskStage stage(String key, StageExecutionStatus status) {
        TaskStage stage = new TaskStage(new StageId(200L + key.length()), TASK_ID, key,
            StageExecutionStatus.PENDING, 1);
        if (status == StageExecutionStatus.RUNNING) {
            Instant startedAt = Instant.parse("2026-08-21T00:00:00Z");
            return stage.start(new ExecutionId("execution-" + key), "worker-test", startedAt,
                startedAt.plusSeconds(60));
        }
        return new TaskStage(stage.id(), stage.taskId(), stage.stageKey(), status, stage.attempt());
    }
}
