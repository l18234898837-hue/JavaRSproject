package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.*;
import com.htht.multisource.application.usecase.model.ArtifactRetentionOperation;
import com.htht.multisource.application.usecase.service.TaskArtifactRetentionService;
import com.htht.multisource.domain.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TaskArtifactRetentionServiceTest {
    @Test void ownerCanRecycleAndRestoreArtifacts() {
        TaskId id = TaskId.of(101L); AccessSubject owner = new AccessSubject(7L); MemoryRetentions retentions = new MemoryRetentions();
        TaskArtifactRetentionService service = new TaskArtifactRetentionService(tasks(id, owner), retentions, () -> Instant.parse("2026-09-30T08:00:00Z"));
        assertThat(service.change(id, owner, ArtifactRetentionOperation.RECYCLE).status()).isEqualTo(TaskArtifactRetentionStatus.RECYCLE_BIN);
        assertThat(service.change(id, owner, ArtifactRetentionOperation.RESTORE).status()).isEqualTo(TaskArtifactRetentionStatus.AVAILABLE);
    }
    private static ProcessingTaskRepository tasks(TaskId id, AccessSubject owner) { var task = ApplicationUseCaseTestFixtures.task(id, owner); return new ProcessingTaskRepository() { public Optional<ProcessingTask> findById(TaskId value) { return Optional.of(task); } public void save(ProcessingTask value) {} }; }
    private static final class MemoryRetentions implements TaskArtifactRetentionRepository { private TaskArtifactRetention value; public Optional<TaskArtifactRetention> find(TaskId id) { return Optional.ofNullable(value); } public TaskArtifactRetention findOrCreate(TaskId id) { return value == null ? value = TaskArtifactRetention.available(id) : value; } public boolean compareAndSet(TaskArtifactRetention expected, TaskArtifactRetention next) { if (!Objects.equals(value, expected)) return false; value = next; return true; } }
}
