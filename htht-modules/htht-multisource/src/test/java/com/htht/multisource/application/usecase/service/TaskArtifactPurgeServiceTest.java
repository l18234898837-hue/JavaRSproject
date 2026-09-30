package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskArtifactPurgePort;
import com.htht.multisource.application.port.out.TaskArtifactRetentionRepository;
import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskHistoryPage;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.application.usecase.model.TaskStageView;
import com.htht.multisource.application.usecase.service.TaskArtifactPurgeService;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TaskArtifactPurgeServiceTest {

    private static final TaskId TASK_ID = TaskId.of(9L);
    private static final AccessSubject OWNER = new AccessSubject(7L);
    private static final Instant NOW = Instant.parse("2026-09-30T08:00:00Z");

    @Test
    void rejectsWrongConfirmationThenPurgesTerminalOwnerTaskAsynchronously() {
        MemoryRetentions retentions = new MemoryRetentions(TaskArtifactRetention.available(TASK_ID).recycle(OWNER, NOW));
        List<Runnable> queuedWork = new ArrayList<>();
        PurgeRecorder purgeRecorder = new PurgeRecorder();
        TaskArtifactPurgeService service = new TaskArtifactPurgeService(
            taskRepository(terminalTask()), retentions, taskHistory("TASK-9"),
            (taskId, subject) -> taskDetail(), purgeRecorder, queuedWork::add, () -> NOW
        );

        assertThatThrownBy(() -> service.request(TASK_ID, OWNER, "other-task"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confirmation");

        assertThat(service.request(TASK_ID, OWNER, "TASK-9").status())
            .isEqualTo(TaskArtifactRetentionStatus.PURGING);
        assertThat(queuedWork).hasSize(1);
        assertThat(purgeRecorder.taskId).isNull();

        queuedWork.get(0).run();

        assertThat(purgeRecorder.taskId).isEqualTo(TASK_ID);
        assertThat(retentions.value.status()).isEqualTo(TaskArtifactRetentionStatus.PURGED);
    }

    @Test
    void rejectsNonterminalTaskWithoutChangingRetention() {
        MemoryRetentions retentions = new MemoryRetentions(TaskArtifactRetention.available(TASK_ID).recycle(OWNER, NOW));
        List<Runnable> queuedWork = new ArrayList<>();
        TaskArtifactPurgeService service = new TaskArtifactPurgeService(
            taskRepository(ApplicationUseCaseTestFixtures.task(TASK_ID, OWNER)), retentions, taskHistory("TASK-9"),
            (taskId, subject) -> taskDetail(), detail -> { }, queuedWork::add, () -> NOW
        );

        assertThatThrownBy(() -> service.request(TASK_ID, OWNER, "TASK-9"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("terminal");

        assertThat(retentions.value.status()).isEqualTo(TaskArtifactRetentionStatus.RECYCLE_BIN);
        assertThat(queuedWork).isEmpty();
    }

    private static ProcessingTask terminalTask() {
        ProcessingTask task = ApplicationUseCaseTestFixtures.task(TASK_ID, OWNER);
        return new ProcessingTask(task.id(), task.name(), task.creator(), TaskLifecycleStatus.SUCCEEDED,
            QualityStatus.NOT_CHECKED, task.parameterSnapshot(), task.outputUri(), task.plan(), task.batch(), task.candidates());
    }

    private static TaskDetailView taskDetail() {
        ProcessingTask task = terminalTask();
        return new TaskDetailView(TASK_ID, task.batch().id(), TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            task.plan(), task.candidates(), List.of(new TaskStageView(new StageId(1L), "READ",
                StageExecutionStatus.SUCCEEDED, 1, "execution-1", null, null)),
            List.of(new TaskArtifactView(new StageId(1L), "SUMMARY", new StorageUri("rs://output/7/task-9/result.json"),
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 42L, "FINAL", "9/output/result.json")));
    }

    private static ProcessingTaskRepository taskRepository(ProcessingTask task) {
        return new ProcessingTaskRepository() {
            @Override
            public Optional<ProcessingTask> findById(TaskId taskId) {
                return task.id().equals(taskId) ? Optional.of(task) : Optional.empty();
            }

            @Override
            public void save(ProcessingTask processingTask) {
            }
        };
    }

    private static TaskHistoryRepository taskHistory(String taskNo) {
        return new TaskHistoryRepository() {
            @Override
            public TaskHistoryPage findPage(TaskHistoryQuery query) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StorageSummary summarize(AccessSubject subject) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<String> findTaskNo(TaskId taskId, AccessSubject subject) {
                return TASK_ID.equals(taskId) && OWNER.equals(subject) ? Optional.of(taskNo) : Optional.empty();
            }
        };
    }

    private static final class MemoryRetentions implements TaskArtifactRetentionRepository {
        private TaskArtifactRetention value;

        private MemoryRetentions(TaskArtifactRetention value) {
            this.value = value;
        }

        @Override
        public Optional<TaskArtifactRetention> find(TaskId taskId) {
            return Optional.ofNullable(value).filter(retention -> retention.taskId().equals(taskId));
        }

        @Override
        public TaskArtifactRetention findOrCreate(TaskId taskId) {
            return value == null ? value = TaskArtifactRetention.available(taskId) : value;
        }

        @Override
        public boolean compareAndSet(TaskArtifactRetention expected, TaskArtifactRetention next) {
            if (!Objects.equals(value, expected)) {
                return false;
            }
            value = next;
            return true;
        }
    }

    private static final class PurgeRecorder implements TaskArtifactPurgePort {
        private TaskId taskId;

        @Override
        public void purge(TaskDetailView detail) {
            taskId = detail.id();
        }
    }
}
