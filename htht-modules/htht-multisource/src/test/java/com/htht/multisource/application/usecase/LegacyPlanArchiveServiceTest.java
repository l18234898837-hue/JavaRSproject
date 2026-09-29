package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.LegacyPlanArchiveRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.service.LegacyPlanArchiveService;
import com.htht.multisource.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("dev")
class LegacyPlanArchiveServiceTest {
    private static final TaskId ID = TaskId.of(101L);

    @Test
    void archivesTerminalLegacyTaskWithoutTouchingStages() {
        var task = task(TaskLifecycleStatus.SUCCEEDED, "plan-v2");
        var archive = new RecordingArchive();
        boolean inserted = new LegacyPlanArchiveService(repo(task), stages(List.of()), archive,
            () -> Instant.parse("2026-09-16T04:00:00Z")).archive(ID, "retirement");
        assertThat(inserted).isTrue();
        assertThat(archive.record.planVersion()).isEqualTo("plan-v2");
    }

    @Test
    void archivesTerminalLegacyTaskWithPendingDownstreamSnapshotStages() {
        var task = task(TaskLifecycleStatus.FAILED, "plan-v1");
        var archive = new RecordingArchive();
        var pending = new TaskStage(new StageId(202L), ID, "WRITE_REPORT", StageExecutionStatus.PENDING, 1);

        boolean inserted = new LegacyPlanArchiveService(repo(task), stages(List.of(pending)), archive,
            () -> Instant.parse("2026-09-16T04:00:00Z")).archive(ID, "retirement");

        assertThat(inserted).isTrue();
        assertThat(archive.record.planVersion()).isEqualTo("plan-v1");
    }

    @Test
    void rejectsNonTerminalLegacyTask() {
        assertThatThrownBy(() -> new LegacyPlanArchiveService(repo(task(TaskLifecycleStatus.RUNNING, "plan-v2")),
            stages(List.of()), new RecordingArchive(), Instant::now).archive(ID, "retirement"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelsNonTerminalLegacyTaskBeforeArchivingWhenCancellationUseCaseIsProvided() {
        var repository = new MutableTaskRepository(task(TaskLifecycleStatus.RUNNING, "plan-v2"));
        var archive = new RecordingArchive();
        var cancel = (com.htht.multisource.application.usecase.CancelProcessingTaskUseCase)
            (taskId, subject) -> repository.task = task(TaskLifecycleStatus.CANCELED, "plan-v2");
        boolean inserted = new LegacyPlanArchiveService(repository, stages(List.of()), archive, Instant::now, cancel)
            .archive(ID, "retirement");
        assertThat(inserted).isTrue();
    }

    @Test
    void rejectsPlanV3AndIsIdempotent() {
        var archive = new RecordingArchive();
        var service = new LegacyPlanArchiveService(repo(task(TaskLifecycleStatus.SUCCEEDED, "plan-v3")), stages(List.of()), archive, Instant::now);
        assertThatThrownBy(() -> service.archive(ID, "retirement")).isInstanceOf(IllegalStateException.class);
        archive.existing = true;
        archive.record = new LegacyPlanArchiveRepository.ArchiveRecord(ID, "plan-v2", Instant.now(), "retirement");
        var legacy = new LegacyPlanArchiveService(repo(task(TaskLifecycleStatus.SUCCEEDED, "plan-v2")), stages(List.of()), archive, Instant::now);
        assertThat(legacy.archive(ID, "retirement")).isFalse();
    }

    private static ProcessingTask task(TaskLifecycleStatus status, String version) {
        var candidate = ApplicationUseCaseTestFixtures.candidate("legacy", ProductSupportStatus.SUPPORTED);
        return new ProcessingTask(ID, "task", ApplicationUseCaseTestFixtures.subject(), status, QualityStatus.NOT_CHECKED,
            "{}", new StorageUri("rs://output/task"), new ProcessingPlan(version,
                List.of(new ProcessingStage("READ", "READ", List.of()))),
            new ProcessingBatch(1L, "batch", ProcessingBatchStatus.CREATED, List.of(candidate)), List.of(candidate));
    }
    private static ProcessingTaskRepository repo(ProcessingTask task) { return new ProcessingTaskRepository() {
        public Optional<ProcessingTask> findById(TaskId id) { return Optional.of(task); }
        public void save(ProcessingTask value) { throw new UnsupportedOperationException(); }
    }; }
    private static final class MutableTaskRepository implements ProcessingTaskRepository {
        private ProcessingTask task;
        private MutableTaskRepository(ProcessingTask task) { this.task = task; }
        public Optional<ProcessingTask> findById(TaskId id) { return Optional.of(task); }
        public void save(ProcessingTask value) { task = value; }
    }
    private static TaskStageRepository stages(List<TaskStage> values) { return new TaskStageRepository() {
        public Optional<TaskStage> findById(StageId id) { return Optional.empty(); }
        public List<TaskStage> findByTaskId(TaskId id) { return values; }
        public void save(TaskStage stage) { throw new UnsupportedOperationException(); }
    }; }
    private static final class RecordingArchive implements LegacyPlanArchiveRepository {
        boolean existing; LegacyPlanArchiveRepository.ArchiveRecord record;
        public Optional<ArchiveRecord> findByTaskId(TaskId id) { return existing ? Optional.of(record) : Optional.empty(); }
        public boolean insertIfAbsent(TaskId id, String version, String reason, Instant at) {
            record = new ArchiveRecord(id, version, at, reason); existing = true; return true;
        }
    }
}
