package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.web.controller.multisource.dto.ProcessingStorageSummaryResponse;
import com.htht.web.controller.multisource.dto.WorkspaceStoragePolicyRequest;
import com.htht.web.controller.multisource.dto.WorkspaceStoragePolicyResponse;
import com.htht.config.multisource.MultiSourceWorkerProperties;
import com.htht.common.helper.LoginHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

@RestController
@RequestMapping("/api/multisource/processing-storage")
public final class ProcessingStorageController {
    private final Supplier<AccessSubject> currentSubject;
    private final TaskHistoryRepository history;
    private final WorkspaceStoragePolicyRepository policies;
    private final Path uploadRoot;
    private final Path artifactRoot;
    private final Path workerRoot;
    private final BooleanSupplier administrator;

    @Autowired
    public ProcessingStorageController(Supplier<AccessSubject> currentSubject, TaskHistoryRepository history,
                                       WorkspaceStoragePolicyRepository policies,
                                       @Value("${file.upload-path}") String uploadPath,
                                       MultiSourceWorkerProperties workerProperties) {
        this(currentSubject, history, policies, Path.of(uploadPath), workerProperties.requireArtifactRoot(),
            workerProperties.getWorkRoot(), LoginHelper::isAdmin);
    }

    ProcessingStorageController(Supplier<AccessSubject> currentSubject, TaskHistoryRepository history,
                                WorkspaceStoragePolicyRepository policies, Path uploadRoot, Path artifactRoot,
                                Path workerRoot, BooleanSupplier administrator) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.history = Objects.requireNonNull(history, "history");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.uploadRoot = normalize(uploadRoot);
        this.artifactRoot = normalize(artifactRoot);
        this.workerRoot = normalize(workerRoot);
        this.administrator = Objects.requireNonNull(administrator, "administrator");
    }

    @GetMapping("/summary")
    public ResponseEntity<R<ProcessingStorageSummaryResponse>> summary() {
        requireAdministrator();
        TaskHistoryRepository.StorageSummary db = history.summarizeAll();
        long source = directorySize(uploadRoot);
        long artifact = directorySize(artifactRoot);
        Path archiveRoot = workerRoot.resolve("archives");
        long worker = directorySizeExcluding(workerRoot, archiveRoot);
        long zip = directorySize(archiveRoot);
        long reclaimable = Math.max(0L, db.recycleBinBytes()) + zip;
        return ResponseEntity.ok(R.ok(new ProcessingStorageSummaryResponse(db.availableArtifactCount(),
            db.recycleBinBytes(), artifact, source, artifact, worker, zip, reclaimable, 0L)));
    }

    @GetMapping("/policy")
    public ResponseEntity<R<WorkspaceStoragePolicyResponse>> policy(
        @RequestParam(defaultValue = "default") String workspaceKey) {
        requireAdministrator();
        return ResponseEntity.ok(R.ok(toResponse(workspaceKey, policies.findVersioned(workspaceKey)
            .orElse(new WorkspaceStoragePolicyRepository.VersionedPolicy(
                WorkspaceStoragePolicyRepository.Policy.defaults(), 0L)))));
    }

    @PatchMapping("/policy")
    public ResponseEntity<R<WorkspaceStoragePolicyResponse>> updatePolicy(
        @RequestParam(defaultValue = "default") String workspaceKey,
        @RequestBody WorkspaceStoragePolicyRequest request) {
        requireAdministrator();
        if (request == null || request.expectedVersion() == null)
            throw new IllegalArgumentException("expectedVersion is required for a storage policy update.");
        var current = policies.findVersioned(workspaceKey).orElse(
            new WorkspaceStoragePolicyRepository.VersionedPolicy(
                WorkspaceStoragePolicyRepository.Policy.defaults(), 0L));
        long expected = request.expectedVersion();
        if (expected != current.version()) throw conflict();
        var base = current.policy();
        var next = new WorkspaceStoragePolicyRepository.Policy(
            request.inputRetentionDays() == null ? base.inputRetentionDays() : request.inputRetentionDays(),
            request.orphanUploadTtlHours() == null ? base.orphanUploadTtlHours() : request.orphanUploadTtlHours(),
            request.workerDirectoryTtlHours() == null ? base.workerDirectoryTtlHours() : request.workerDirectoryTtlHours(),
            request.autoCleanupEnabled() == null ? base.autoCleanupEnabled() : request.autoCleanupEnabled());
        try {
            return ResponseEntity.ok(R.ok(toResponse(workspaceKey, policies.update(workspaceKey, next, expected))));
        } catch (IllegalStateException exception) {
            throw conflict();
        }
    }

    private static WorkspaceStoragePolicyResponse toResponse(String key, WorkspaceStoragePolicyRepository.VersionedPolicy value) {
        var p = value.policy();
        return new WorkspaceStoragePolicyResponse(key, p.inputRetentionDays(), p.orphanUploadTtlHours(),
            p.workerDirectoryTtlHours(), p.autoCleanupEnabled(), value.version());
    }

    private static Path normalize(Path path) { return Objects.requireNonNull(path, "storage root").toAbsolutePath().normalize(); }

    private void requireAdministrator() {
        if (!administrator.getAsBoolean()) throw new SecurityException("Administrator permission is required.");
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Workspace storage policy changed concurrently.");
    }

    private static long directorySize(Path root) {
        if (!Files.isDirectory(root)) return 0L;
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).mapToLong(file -> {
                try { return Files.size(file); } catch (IOException ignored) { return 0L; }
            }).sum();
        } catch (IOException | UncheckedIOException ignored) { return 0L; }
    }

    private static long directorySizeExcluding(Path root, Path excludedRoot) {
        if (!Files.isDirectory(root)) return 0L;
        Path excluded = excludedRoot.toAbsolutePath().normalize();
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).filter(file -> !file.toAbsolutePath().normalize().startsWith(excluded))
                .mapToLong(file -> {
                    try { return Files.size(file); } catch (IOException ignored) { return 0L; }
                }).sum();
        } catch (IOException | UncheckedIOException ignored) { return 0L; }
    }
}
