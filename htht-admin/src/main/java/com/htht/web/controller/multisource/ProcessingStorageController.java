package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.web.controller.multisource.dto.ProcessingStorageSummaryResponse;
import com.htht.web.controller.multisource.dto.WorkspaceStoragePolicyRequest;
import com.htht.web.controller.multisource.dto.WorkspaceStoragePolicyResponse;
import com.htht.config.multisource.MultiSourceWorkerProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/multisource/processing-storage")
public final class ProcessingStorageController {
    private final Supplier<AccessSubject> currentSubject;
    private final TaskHistoryRepository history;
    private final WorkspaceStoragePolicyRepository policies;
    private final Path uploadRoot;
    private final Path artifactRoot;
    private final Path workerRoot;

    public ProcessingStorageController(Supplier<AccessSubject> currentSubject, TaskHistoryRepository history,
                                       WorkspaceStoragePolicyRepository policies,
                                       @Value("${file.upload-path}") String uploadPath,
                                       MultiSourceWorkerProperties workerProperties) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.history = Objects.requireNonNull(history, "history");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.uploadRoot = normalize(Path.of(uploadPath));
        this.artifactRoot = normalize(workerProperties.requireArtifactRoot());
        this.workerRoot = normalize(workerProperties.getWorkRoot());
    }

    @GetMapping("/summary")
    public ResponseEntity<R<ProcessingStorageSummaryResponse>> summary() {
        TaskHistoryRepository.StorageSummary db = history.summarize(currentSubject.get());
        long source = directorySize(uploadRoot);
        long artifact = directorySize(artifactRoot);
        long worker = directorySize(workerRoot);
        long zip = directorySize(artifactRoot.resolve("archives"));
        long reclaimable = Math.max(0L, db.recycleBinBytes()) + zip;
        return ResponseEntity.ok(R.ok(new ProcessingStorageSummaryResponse(db.availableArtifactCount(),
            db.recycleBinBytes(), artifact, source, artifact, worker, zip, reclaimable, 0L)));
    }

    @GetMapping("/policy")
    public ResponseEntity<R<WorkspaceStoragePolicyResponse>> policy(
        @RequestParam(defaultValue = "default") String workspaceKey) {
        return ResponseEntity.ok(R.ok(toResponse(workspaceKey, policies.findVersioned(workspaceKey)
            .orElse(new WorkspaceStoragePolicyRepository.VersionedPolicy(
                WorkspaceStoragePolicyRepository.Policy.defaults(), 0L)))));
    }

    @PatchMapping("/policy")
    public ResponseEntity<R<WorkspaceStoragePolicyResponse>> updatePolicy(
        @RequestParam(defaultValue = "default") String workspaceKey,
        @RequestBody WorkspaceStoragePolicyRequest request) {
        if (request == null) throw new IllegalArgumentException("Storage policy is required.");
        var current = policies.findVersioned(workspaceKey).orElse(
            new WorkspaceStoragePolicyRepository.VersionedPolicy(
                WorkspaceStoragePolicyRepository.Policy.defaults(), 0L));
        long expected = request.expectedVersion() == null ? current.version() : request.expectedVersion();
        if (expected != current.version()) throw new IllegalStateException("Workspace storage policy changed concurrently.");
        var base = current.policy();
        var next = new WorkspaceStoragePolicyRepository.Policy(
            request.inputRetentionDays() == null ? base.inputRetentionDays() : request.inputRetentionDays(),
            request.orphanUploadTtlHours() == null ? base.orphanUploadTtlHours() : request.orphanUploadTtlHours(),
            request.workerDirectoryTtlHours() == null ? base.workerDirectoryTtlHours() : request.workerDirectoryTtlHours(),
            request.autoCleanupEnabled() == null ? base.autoCleanupEnabled() : request.autoCleanupEnabled());
        return ResponseEntity.ok(R.ok(toResponse(workspaceKey, policies.update(workspaceKey, next, expected))));
    }

    private static WorkspaceStoragePolicyResponse toResponse(String key, WorkspaceStoragePolicyRepository.VersionedPolicy value) {
        var p = value.policy();
        return new WorkspaceStoragePolicyResponse(key, p.inputRetentionDays(), p.orphanUploadTtlHours(),
            p.workerDirectoryTtlHours(), p.autoCleanupEnabled(), value.version());
    }

    private static Path normalize(Path path) { return Objects.requireNonNull(path, "storage root").toAbsolutePath().normalize(); }

    private static long directorySize(Path root) {
        if (!Files.isDirectory(root)) return 0L;
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).mapToLong(file -> {
                try { return Files.size(file); } catch (IOException ignored) { return 0L; }
            }).sum();
        } catch (IOException ignored) { return 0L; }
    }
}
