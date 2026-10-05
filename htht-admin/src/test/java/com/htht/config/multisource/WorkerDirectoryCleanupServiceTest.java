package com.htht.config.multisource;

import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class WorkerDirectoryCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    @TempDir Path workRoot;

    @Test void runningAndRetryingExecutionDirectoriesAreAlwaysActive() throws Exception {
        Path running = directory("running");
        Path retrying = directory("retrying");
        Map<Path, WorkerDirectoryCleanupService.ExecutionOwnership> ownership = Map.of(
            running, ownership("running", TaskLifecycleStatus.RUNNING, StageExecutionStatus.RUNNING, NOW.minusSeconds(7 * 86400)),
            retrying, ownership("retrying", TaskLifecycleStatus.FAILED, StageExecutionStatus.RETRYING, NOW.minusSeconds(7 * 86400)));

        var result = service(ownership, (path, value) -> false).scanAndCleanup(NOW);

        assertThat(result.directories()).extracting(WorkerDirectoryCleanupService.DirectoryRecord::classification)
            .containsExactly(WorkerDirectoryCleanupService.Classification.ACTIVE, WorkerDirectoryCleanupService.Classification.ACTIVE);
        assertThat(running).exists();
        assertThat(retrying).exists();
    }

    @Test void terminalDirectoryRespectsWorkerTtlBeforeDeleting() throws Exception {
        Path fresh = directory("fresh");
        Path expired = directory("expired");
        Map<Path, WorkerDirectoryCleanupService.ExecutionOwnership> ownership = Map.of(
            fresh, ownership("fresh", TaskLifecycleStatus.SUCCEEDED, StageExecutionStatus.SUCCEEDED, NOW.minusSeconds(47 * 3600)),
            expired, ownership("expired", TaskLifecycleStatus.SUCCEEDED, StageExecutionStatus.SUCCEEDED, NOW.minusSeconds(48 * 3600)));

        var result = service(ownership, (path, value) -> false).scanAndCleanup(NOW);

        assertThat(record(result, fresh).classification()).isEqualTo(WorkerDirectoryCleanupService.Classification.PROTECTED);
        assertThat(record(result, expired).classification()).isEqualTo(WorkerDirectoryCleanupService.Classification.EXPIRED);
        assertThat(fresh).exists();
        assertThat(expired).doesNotExist();
    }

    @Test void unmatchedDirectoriesNeedTwentyFourHoursAndNoProcessLock() throws Exception {
        Path old = directory("unmatched-old");
        Path recent = directory("unmatched-recent");
        Path locked = directory("unmatched-locked");
        Files.setLastModifiedTime(old, FileTime.from(NOW.minusSeconds(24 * 3600)));
        Files.setLastModifiedTime(recent, FileTime.from(NOW.minusSeconds(23 * 3600)));
        Files.setLastModifiedTime(locked, FileTime.from(NOW.minusSeconds(72 * 3600)));

        var result = service(Map.of(), (path, value) -> path.equals(locked)).scanAndCleanup(NOW);

        assertThat(record(result, old).classification()).isEqualTo(WorkerDirectoryCleanupService.Classification.ORPHANED);
        assertThat(record(result, recent).classification()).isEqualTo(WorkerDirectoryCleanupService.Classification.PROTECTED);
        assertThat(record(result, locked).classification()).isEqualTo(WorkerDirectoryCleanupService.Classification.ACTIVE);
        assertThat(old).doesNotExist();
        assertThat(recent).exists();
        assertThat(locked).exists();
    }

    @Test void protectedDirectoriesAndDeleteFailuresAreRetainedForLaterRetry() throws Exception {
        Path archive = directory("archives");
        Path failing = directory("failing");
        Map<Path, WorkerDirectoryCleanupService.ExecutionOwnership> ownership = Map.of(
            failing, ownership("failing", TaskLifecycleStatus.CANCELED, StageExecutionStatus.SKIPPED, NOW.minusSeconds(72 * 3600)));
        var service = service(ownership, (path, value) -> false, path -> { throw new IOException("disk busy"); });

        var result = service.scanAndCleanup(NOW);

        assertThat(record(result, archive).classification()).isEqualTo(WorkerDirectoryCleanupService.Classification.PROTECTED);
        assertThat(record(result, failing).failure()).isEqualTo("disk busy");
        assertThat(result.failures()).containsEntry(failing.toAbsolutePath().normalize(), "disk busy");
        assertThat(archive).exists();
        assertThat(failing).exists();
    }

    private Path directory(String name) throws IOException {
        Path directory = Files.createDirectories(workRoot.resolve(name));
        Files.writeString(directory.resolve("worker.tmp"), "payload");
        return directory.toAbsolutePath().normalize();
    }

    private WorkerDirectoryCleanupService service(Map<Path, WorkerDirectoryCleanupService.ExecutionOwnership> ownership,
                                                  WorkerDirectoryCleanupService.WorkerProcessLockInspector locks) {
        return service(ownership, locks, path -> {
            try (var files = Files.walk(path)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(candidate -> {
                    try { Files.deleteIfExists(candidate); } catch (IOException exception) { throw new RuntimeException(exception); }
                });
            }
        });
    }

    private WorkerDirectoryCleanupService service(Map<Path, WorkerDirectoryCleanupService.ExecutionOwnership> ownership,
                                                  WorkerDirectoryCleanupService.WorkerProcessLockInspector locks,
                                                  WorkerDirectoryCleanupService.FileTreeDeleter deleter) {
        Map<Path, WorkerDirectoryCleanupService.ExecutionOwnership> values = new HashMap<>(ownership);
        WorkspaceStoragePolicyRepository policies = workspace -> Optional.of(new WorkspaceStoragePolicyRepository.Policy(14, 24, 48, false));
        return new WorkerDirectoryCleanupService(workRoot, directory -> Optional.ofNullable(values.get(directory)), locks,
            policies, path -> path.getFileName().toString().equals("archives"), deleter);
    }

    private static WorkerDirectoryCleanupService.ExecutionOwnership ownership(String id, TaskLifecycleStatus task,
                                                                                StageExecutionStatus stage, Instant finishedAt) {
        return new WorkerDirectoryCleanupService.ExecutionOwnership(id, "default", task, stage, finishedAt);
    }

    private static WorkerDirectoryCleanupService.DirectoryRecord record(WorkerDirectoryCleanupService.CleanupResult result, Path path) {
        return result.directories().stream().filter(value -> value.directory().equals(path)).findFirst().orElseThrow();
    }
}
