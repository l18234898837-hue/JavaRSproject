package com.htht.web.controller.multisource;

import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.domain.model.AccessSubject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ProcessingStorageControllerTest {
    @TempDir Path temp;

    @Test
    void reportsWorkerAndArchiveBytesAsMutuallyExclusiveCategories() throws Exception {
        Path upload = Files.createDirectory(temp.resolve("upload"));
        Path artifacts = Files.createDirectory(temp.resolve("artifacts"));
        Path work = Files.createDirectory(temp.resolve("work"));
        Files.write(upload.resolve("source.bin"), new byte[7]);
        Files.write(artifacts.resolve("result.bin"), new byte[5]);
        Files.write(work.resolve("worker.bin"), new byte[2]);
        Path archives = Files.createDirectory(work.resolve("archives"));
        Files.write(archives.resolve("task.zip"), new byte[3]);
        ProcessingStorageController controller = controller(upload, artifacts, work, true);

        var summary = controller.summary().getBody().getData();

        assertThat(summary.sourceBytes()).isEqualTo(7L);
        assertThat(summary.artifactBytes()).isEqualTo(5L);
        assertThat(summary.workerBytes()).isEqualTo(2L);
        assertThat(summary.zipBytes()).isEqualTo(3L);
    }

    @Test
    void rejectsGlobalStorageReadsForNonAdministrators() {
        ProcessingStorageController controller = controller(temp.resolve("upload"), temp.resolve("artifacts"), temp.resolve("work"), false);

        assertThatThrownBy(controller::summary)
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Administrator");
    }

    @Test
    void requiresMatchingVersionForPolicyUpdates() {
        ProcessingStorageController controller = controller(temp.resolve("upload"), temp.resolve("artifacts"), temp.resolve("work"), true);

        assertThatThrownBy(() -> controller.updatePolicy("default",
            new com.htht.web.controller.multisource.dto.WorkspaceStoragePolicyRequest(null, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expectedVersion");
        assertThatThrownBy(() -> controller.updatePolicy("default",
            new com.htht.web.controller.multisource.dto.WorkspaceStoragePolicyRequest(null, null, null, null, 4L)))
            .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                error -> assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT))
            .hasMessageContaining("concurrently");
    }

    private static ProcessingStorageController controller(Path upload, Path artifacts, Path work, boolean admin) {
        TaskHistoryRepository history = new TaskHistoryRepository() {
            @Override public com.htht.multisource.application.usecase.model.TaskHistoryPage findPage(com.htht.multisource.application.usecase.model.TaskHistoryQuery query) { throw new UnsupportedOperationException(); }
            @Override public StorageSummary summarize(AccessSubject subject) { return new StorageSummary(2L, 11L, 99L); }
            @Override public StorageSummary summarizeAll() { return new StorageSummary(2L, 11L, 99L); }
        };
        WorkspaceStoragePolicyRepository policies = new WorkspaceStoragePolicyRepository() {
            private final Policy policy = Policy.defaults();
            @Override public Optional<Policy> findByWorkspace(String workspace) { return Optional.of(policy); }
            @Override public Optional<VersionedPolicy> findVersioned(String workspace) { return Optional.of(new VersionedPolicy(policy, 3L)); }
            @Override public VersionedPolicy update(String workspace, Policy next, long version) {
                if (version != 3L) throw new IllegalStateException("Workspace storage policy changed concurrently.");
                return new VersionedPolicy(next, 4L);
            }
        };
        return new ProcessingStorageController(() -> new AccessSubject(7L), history, policies, upload, artifacts, work, () -> admin);
    }
}
