package com.htht.config.multisource;

import com.htht.multisource.application.port.out.WorkspaceStoragePolicyRepository;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Removes expired, task-owned worker directories independently of source and
 * artifact retention. Ownership is supplied by the persistence layer; this
 * class intentionally never treats a directory name as proof of ownership.
 */
public final class WorkerDirectoryCleanupService {
    private static final Duration ORPHAN_TTL = Duration.ofHours(24);
    private static final String DEFAULT_WORKSPACE = "default";

    private final Path workRoot;
    private final WorkerDirectoryOwnershipResolver ownershipResolver;
    private final WorkerProcessLockInspector processLocks;
    private final WorkspaceStoragePolicyRepository policies;
    private final Predicate<Path> protectedDirectory;
    private final FileTreeDeleter deleter;
    private final Map<Path, String> failures = new LinkedHashMap<>();

    public WorkerDirectoryCleanupService(Path workRoot, WorkerDirectoryOwnershipResolver ownershipResolver,
                                         WorkerProcessLockInspector processLocks,
                                         WorkspaceStoragePolicyRepository policies) {
        this(workRoot, ownershipResolver, processLocks, policies,
            path -> path.getFileName() != null && "archives".equals(path.getFileName().toString()),
            WorkerDirectoryCleanupService::deleteTree);
    }

    WorkerDirectoryCleanupService(Path workRoot, WorkerDirectoryOwnershipResolver ownershipResolver,
                                  WorkerProcessLockInspector processLocks,
                                  WorkspaceStoragePolicyRepository policies,
                                  Predicate<Path> protectedDirectory, FileTreeDeleter deleter) {
        this.workRoot = Objects.requireNonNull(workRoot, "workRoot").toAbsolutePath().normalize();
        this.ownershipResolver = Objects.requireNonNull(ownershipResolver, "ownershipResolver");
        this.processLocks = Objects.requireNonNull(processLocks, "processLocks");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.protectedDirectory = Objects.requireNonNull(protectedDirectory, "protectedDirectory");
        this.deleter = Objects.requireNonNull(deleter, "deleter");
    }

    /** Classifies immediate worker execution directories without mutating them. */
    public ScanResult scan(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!Files.isDirectory(workRoot)) return new ScanResult(List.of(), Map.copyOf(failures));
        List<DirectoryRecord> records = new ArrayList<>();
        try (var children = Files.list(workRoot)) {
            children.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(path -> records.add(classify(path, now)));
        } catch (IOException ignored) {
            // A subsequent scheduled scan will retry. Existing metadata is never changed here.
        }
        return new ScanResult(List.copyOf(records), Map.copyOf(failures));
    }

    /**
     * Compatibility overload for callers that make the scanned root explicit.
     * A cleaner may never be redirected outside its configured work root.
     */
    public ScanResult scan(Path configuredWorkRoot, Instant now) {
        Path candidate = Objects.requireNonNull(configuredWorkRoot, "configuredWorkRoot").toAbsolutePath().normalize();
        if (!workRoot.equals(candidate)) throw new IllegalArgumentException("Worker cleanup root does not match configuration.");
        return scan(now);
    }

    /** Deletes only EXPIRED task-owned or ORPHANED unlocked directories. */
    public CleanupResult scanAndCleanup(Instant now) {
        ScanResult scan = scan(now);
        List<DirectoryRecord> results = new ArrayList<>();
        int deleted = 0;
        for (DirectoryRecord record : scan.directories()) {
            if (!record.classification().isDeletable()) {
                results.add(record);
                continue;
            }
            try {
                deleteVerified(record.directory());
                failures.remove(record.directory());
                results.add(record.deleted());
                deleted++;
            } catch (IOException exception) {
                String failure = safeFailure(exception);
                failures.put(record.directory(), failure);
                results.add(record.failed(failure));
            }
        }
        return new CleanupResult(List.copyOf(results), deleted, Map.copyOf(failures));
    }

    public Map<Path, String> failures() {
        return Map.copyOf(failures);
    }

    private DirectoryRecord classify(Path directory, Instant now) {
        Path canonical = directory.toAbsolutePath().normalize();
        if (!canonical.startsWith(workRoot) || Files.isSymbolicLink(canonical) || protectedDirectory.test(canonical)) {
            return new DirectoryRecord(canonical, Classification.PROTECTED, "protected directory", false, null);
        }
        Optional<ExecutionOwnership> resolution;
        try {
            resolution = ownershipResolver.resolve(canonical);
        } catch (RuntimeException exception) {
            return new DirectoryRecord(canonical, Classification.PROTECTED, "ownership lookup failed", false, safeFailure(exception));
        }
        ExecutionOwnership ownership = resolution.orElse(null);
        if (isLocked(canonical, ownership)) {
            return new DirectoryRecord(canonical, Classification.ACTIVE, "worker process lock is held", false, null);
        }
        if (ownership == null) {
            return isOlderThan(canonical, now, ORPHAN_TTL)
                ? new DirectoryRecord(canonical, Classification.ORPHANED, "unmatched directory exceeded 24 hour TTL", false, null)
                : new DirectoryRecord(canonical, Classification.PROTECTED, "unmatched directory is still within 24 hour TTL", false, null);
        }
        if (!ownership.isTerminal()) {
            return new DirectoryRecord(canonical, Classification.ACTIVE, "task or stage is not terminal", false, null);
        }
        if (ownership.finishedAt() == null) {
            return new DirectoryRecord(canonical, Classification.PROTECTED, "terminal execution has no completion timestamp", false, null);
        }
        WorkspaceStoragePolicyRepository.Policy policy = policies.findByWorkspace(ownership.workspaceKey())
            .orElseGet(WorkspaceStoragePolicyRepository.Policy::defaults);
        Duration ttl = Duration.ofHours(policy.workerDirectoryTtlHours());
        if (ownership.finishedAt().plus(ttl).isAfter(now)) {
            return new DirectoryRecord(canonical, Classification.PROTECTED, "terminal execution is within worker TTL", false, null);
        }
        return new DirectoryRecord(canonical, Classification.EXPIRED, "terminal execution exceeded worker TTL", false, null);
    }

    private boolean isLocked(Path directory, ExecutionOwnership ownership) {
        try {
            return processLocks.isLocked(directory, ownership);
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static boolean isOlderThan(Path path, Instant now, Duration ttl) {
        try {
            FileTime modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
            return !modified.toInstant().isAfter(now.minus(ttl));
        } catch (IOException exception) {
            return false;
        }
    }

    private void deleteVerified(Path directory) throws IOException {
        Path canonical = directory.toAbsolutePath().normalize();
        if (!canonical.startsWith(workRoot) || canonical.equals(workRoot) || Files.isSymbolicLink(canonical)) {
            throw new IOException("Refusing to delete a path outside the configured worker root.");
        }
        deleter.delete(canonical);
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) throw exception;
                Files.deleteIfExists(directory); return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String safeFailure(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public enum Classification {
        ACTIVE, EXPIRED, ORPHANED, PROTECTED;
        boolean isDeletable() { return this == EXPIRED || this == ORPHANED; }
    }

    public record ExecutionOwnership(String executionId, String workspaceKey, TaskLifecycleStatus taskStatus,
                                     StageExecutionStatus stageStatus, Instant finishedAt) {
        public ExecutionOwnership {
            if (executionId == null || executionId.isBlank()) throw new IllegalArgumentException("executionId is required.");
            workspaceKey = workspaceKey == null || workspaceKey.isBlank() ? DEFAULT_WORKSPACE : workspaceKey;
            Objects.requireNonNull(taskStatus, "taskStatus");
            Objects.requireNonNull(stageStatus, "stageStatus");
        }
        boolean isTerminal() {
            return switch (taskStatus) {
                case SUCCEEDED, FAILED, CANCELED -> switch (stageStatus) {
                    case SUCCEEDED, FAILED, SKIPPED -> true;
                    default -> false;
                };
                default -> false;
            };
        }
    }

    @FunctionalInterface public interface WorkerDirectoryOwnershipResolver {
        Optional<ExecutionOwnership> resolve(Path directory);
    }
    @FunctionalInterface public interface WorkerProcessLockInspector {
        boolean isLocked(Path directory, ExecutionOwnership ownership);
    }
    @FunctionalInterface interface FileTreeDeleter { void delete(Path directory) throws IOException; }

    public record DirectoryRecord(Path directory, Classification classification, String reason,
                                  boolean wasDeleted, String failure) {
        DirectoryRecord deleted() { return new DirectoryRecord(directory, classification, reason, true, null); }
        DirectoryRecord failed(String value) { return new DirectoryRecord(directory, classification, reason, false, value); }
    }
    public record ScanResult(List<DirectoryRecord> directories, Map<Path, String> failures) { }
    public record CleanupResult(List<DirectoryRecord> directories, int deletedCount, Map<Path, String> failures) { }
}
