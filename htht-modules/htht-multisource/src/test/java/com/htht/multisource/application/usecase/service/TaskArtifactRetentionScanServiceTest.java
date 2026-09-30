package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.TaskArtifactAvailabilityProbe;
import com.htht.multisource.application.port.out.TaskArtifactRetentionRepository;
import com.htht.multisource.application.port.out.TaskArtifactRetentionScanSource;
import com.htht.multisource.application.usecase.model.TaskArtifactRetentionScanCandidate;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TaskArtifactRetentionScanServiceTest {
    private static final AccessSubject OWNER = new AccessSubject(7L);
    private static final Instant NOW = Instant.parse("2026-09-30T08:00:00Z");

    @Test
    void doesNotOverwriteAnExistingPinnedRetentionWhenScanSourceIsStale() {
        TaskId availableTask = TaskId.of(1L);
        TaskId pinnedTask = TaskId.of(2L);
        InMemoryRetentionRepository retentions = new InMemoryRetentionRepository();
        retentions.records.put(pinnedTask, TaskArtifactRetention.available(pinnedTask).pin(OWNER, NOW));
        TaskArtifactRetentionScanSource source = () -> List.of(
            new TaskArtifactRetentionScanCandidate(availableTask, List.of("1/output/result.tif")),
            new TaskArtifactRetentionScanCandidate(pinnedTask, List.of("2/output/result.tif"))
        );
        TaskArtifactAvailabilityProbe probe = candidate -> candidate.taskId().equals(availableTask)
            ? TaskArtifactRetentionStatus.AVAILABLE : TaskArtifactRetentionStatus.MISSING;
        TaskArtifactRetentionScanService service = new TaskArtifactRetentionScanService(source, retentions, probe);

        var result = service.scan();

        assertThat(result.createdAvailable()).isEqualTo(1);
        assertThat(result.createdMissing()).isZero();
        assertThat(result.skippedExisting()).isEqualTo(1);
        assertThat(retentions.find(pinnedTask).orElseThrow().status()).isEqualTo(TaskArtifactRetentionStatus.PINNED);
        assertThat(retentions.find(availableTask).orElseThrow().status()).isEqualTo(TaskArtifactRetentionStatus.AVAILABLE);
    }

    @Test
    void recordsUnreadableMetadataWithoutCreatingOrChangingRetention() {
        TaskId taskId = TaskId.of(3L);
        InMemoryRetentionRepository retentions = new InMemoryRetentionRepository();
        TaskArtifactRetentionScanSource source = () -> List.of(
            new TaskArtifactRetentionScanCandidate(taskId, List.of("../outside/result.tif"))
        );
        TaskArtifactAvailabilityProbe probe = candidate -> {
            throw new IllegalArgumentException("Artifact storage key is invalid.");
        };
        TaskArtifactRetentionScanService service = new TaskArtifactRetentionScanService(source, retentions, probe);

        var result = service.scan();

        assertThat(result.createdAvailable()).isZero();
        assertThat(result.createdMissing()).isZero();
        assertThat(result.skippedExisting()).isZero();
        assertThat(result.unreadableMetadata()).isEqualTo(1);
        assertThat(retentions.find(taskId)).isEmpty();
    }

    private static final class InMemoryRetentionRepository implements TaskArtifactRetentionRepository {
        private final Map<TaskId, TaskArtifactRetention> records = new LinkedHashMap<>();

        @Override
        public Optional<TaskArtifactRetention> find(TaskId taskId) {
            return Optional.ofNullable(records.get(taskId));
        }

        @Override
        public TaskArtifactRetention findOrCreate(TaskId taskId) {
            return records.computeIfAbsent(taskId, TaskArtifactRetention::available);
        }

        @Override
        public boolean createIfAbsent(TaskArtifactRetention retention) {
            return records.putIfAbsent(retention.taskId(), retention) == null;
        }

        @Override
        public boolean compareAndSet(TaskArtifactRetention expected, TaskArtifactRetention next) {
            return records.replace(expected.taskId(), expected, next);
        }
    }
}
