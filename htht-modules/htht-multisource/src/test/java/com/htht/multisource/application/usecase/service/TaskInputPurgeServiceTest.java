package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ManagedInputFilePurgePort;
import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import com.htht.multisource.application.usecase.TaskInputRetentionUseCase;
import com.htht.multisource.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TaskInputPurgeServiceTest {
    @Test
    void refusesToCompleteWhenTaskHasNoManagedSourceReferences() {
        AccessSubject subject = new AccessSubject(7);
        TaskId taskId = TaskId.of(42);
        FakeRetention retention = new FakeRetention(taskId);
        FakeReferences references = new FakeReferences();
        TaskInputPurgeService service = new TaskInputPurgeService(
            new FakeTasks(taskId, subject), new FakeHistory(), retention, references,
            new FakeFiles(), managed -> { throw new AssertionError("purge must not run"); },
            Runnable::run, Instant::now);

        service.request(taskId, subject, "TASK-42", "default");

        assertThat(retention.completed).isFalse();
        assertThat(retention.failed).isTrue();
        assertThat(retention.failureReason).contains("No managed source inputs are registered");
    }

    @Test
    void requestsAtomicPurgeTransitionBeforeStartingAsyncCleanup() {
        AccessSubject subject = new AccessSubject(7);
        TaskId taskId = TaskId.of(42);
        FakeRetention retention = new FakeRetention(taskId);
        TaskInputPurgeService service = new TaskInputPurgeService(
            new FakeTasks(taskId, subject), new FakeHistory(), retention, new FakeReferences(),
            new FakeFiles(), managed -> { throw new AssertionError("purge must not run"); },
            command -> { }, Instant::now);

        service.request(taskId, subject, "TASK-42", "default");

        assertThat(retention.atomicRequestCalled).isTrue();
    }

    private static final class FakeTasks implements ProcessingTaskRepository {
        private final ProcessingTask task;
        FakeTasks(TaskId id, AccessSubject creator) {
            ProductProfileKey key = new ProductProfileKey("P", "1");
            InputAsset input = new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "99"),
                new StorageUri("rs://sys-file/99"), "sha256:input", true);
            ProductCandidate candidate = new ProductCandidate("pkg", key, ProductDataShape.RASTER_GRID,
                ProductSupportStatus.SUPPORTED, List.of(input), 1.0);
            ProcessingStage stage = new ProcessingStage("S", "S", List.of(), List.of(), List.of(), "{}");
            task = new ProcessingTask(id, "task", creator, TaskLifecycleStatus.SUCCEEDED,
                QualityStatus.NOT_CHECKED, "{}", new StorageUri("rs://output"),
                new ProcessingPlan("1", "processor", List.of(stage)),
                new ProcessingBatch(1, "batch", ProcessingBatchStatus.COMPLETED, List.of(candidate)),
                List.of(candidate));
        }
        @Override public Optional<ProcessingTask> findById(TaskId id) { return Optional.of(task); }
        @Override public void save(ProcessingTask task) { }
    }

    private static final class FakeHistory implements TaskHistoryRepository {
        @Override public com.htht.multisource.application.usecase.model.TaskHistoryPage findPage(
            com.htht.multisource.application.usecase.model.TaskHistoryQuery query) { return null; }
        @Override public StorageSummary summarize(AccessSubject subject) { return null; }
        @Override public Optional<String> findTaskNo(TaskId taskId, AccessSubject subject) { return Optional.of("TASK-42"); }
    }

    private static final class FakeReferences implements TaskInputReferenceRepository {
        @Override public void findOrRegister(TaskId taskId, long managedFileId, String sourceType, String sourceId, String checksum) { }
        @Override public List<Long> findActiveReferences(long managedFileId) { return List.of(); }
        @Override public List<Long> findActiveManagedFiles(TaskId taskId) { return List.of(); }
        @Override public void releaseForTask(TaskId taskId, long operatorId, Instant now) { }
    }

    private static final class FakeFiles implements ManagedInputFileRepository {
        @Override public long findOrRegister(String workspaceKey, long sysFileId, String path, String checksum, long sizeBytes) { return 1; }
    }

    private static final class FakeRetention implements TaskInputRetentionUseCase {
        private final TaskId taskId;
        private TaskInputRetention value;
        private boolean completed;
        private boolean failed;
        private boolean atomicRequestCalled;
        private String failureReason;
        FakeRetention(TaskId taskId) { this.taskId = taskId; }
        @Override public TaskInputRetention schedule(TaskId id, String workspace, Instant expiresAt, AccessSubject operator) {
            Instant now = Instant.now();
            value = new TaskInputRetention(id, workspace, TaskInputRetentionStatus.SCHEDULED,
                now.minusSeconds(1), now, null, null, now, operator.userId(), null,
                value == null ? 0L : value.version() + 1);
            return value;
        }
        @Override public TaskInputRetention requestPurge(TaskId id, String workspace, AccessSubject operator, Instant now) {
            atomicRequestCalled = true;
            value = new TaskInputRetention(id, workspace, TaskInputRetentionStatus.PURGING,
                now, now, now, null, now, null, null, 1L);
            return value;
        }
        @Override public TaskInputRetention changePolicy(TaskId id, String workspace, TaskInputRetention.PolicyChoice choice, AccessSubject operator) { throw new UnsupportedOperationException(); }
        @Override public TaskInputRetention claim(TaskId id, long expectedVersion, Instant now) {
            value = value.claim(now);
            return value;
        }
        @Override public Optional<TaskInputRetention> find(TaskId id) { return Optional.ofNullable(value); }
        @Override public TaskInputRetention complete(TaskId id, AccessSubject operator, Instant now) { completed = true; return value; }
        @Override public TaskInputRetention fail(TaskId id, AccessSubject operator, Instant now, String reason) { failed = true; failureReason = reason; return value; }
    }
}
