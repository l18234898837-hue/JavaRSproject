package com.htht.config.multisource;

import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class StorageCleanupSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    @TempDir Path temp;

    @Test
    void dryRunReportsEligibleCandidatesWithoutMutatingTheirFilesOrDirectories() throws Exception {
        Path uploadRoot = Files.createDirectories(temp.resolve("uploads"));
        Path orphan = Files.writeString(uploadRoot.resolve("expired.uploading"), "orphan-data");
        Files.setLastModifiedTime(orphan, FileTime.from(NOW.minus(Duration.ofHours(25))));
        OrphanUploadCleanupService orphans = new OrphanUploadCleanupService(uploadRoot, new EmptyManagedFiles(), path -> false,
            Duration.ofHours(24));

        Path workRoot = Files.createDirectories(temp.resolve("work"));
        Path workerDirectory = Files.createDirectories(workRoot.resolve("execution-1"));
        Path workerFile = Files.writeString(workerDirectory.resolve("result.tmp"), "worker-data");
        WorkerDirectoryCleanupService workers = new WorkerDirectoryCleanupService(workRoot,
            directory -> Optional.of(new WorkerDirectoryCleanupService.ExecutionOwnership("execution-1", "workspace-a",
                TaskLifecycleStatus.SUCCEEDED, StageExecutionStatus.SUCCEEDED, NOW.minus(Duration.ofHours(49)))),
            (directory, ownership) -> false,
            workspace -> Optional.of(new WorkspaceStoragePolicyRepository.Policy(14, 24, 48, false)));
        var reports = new ArrayList<StorageCleanupScheduler.CleanupRunSummary>();
        StorageCleanupScheduler scheduler = new StorageCleanupScheduler(orphans, workers, false, true, true, true, reports::add);

        scheduler.scan(NOW);

        assertThat(orphan).exists();
        assertThat(workerDirectory).exists();
        assertThat(workerFile).exists();
        assertThat(reports).singleElement().satisfies(report -> {
            assertThat(report.dryRun()).isTrue();
            assertThat(report.workspace()).isEqualTo("all-configured");
            assertThat(report.candidateCount()).isEqualTo(2);
            assertThat(report.candidateBytes()).isEqualTo(Files.size(orphan) + Files.size(workerFile));
            assertThat(report.protectedCount()).isZero();
            assertThat(report.failureCount()).isZero();
        });
    }

    private static final class EmptyManagedFiles implements ManagedInputFileRepository {
        @Override public long findOrRegister(String workspaceKey, long sysFileId, String path, String checksum, long sizeBytes) { return 0; }
    }
}
