package com.htht.config.multisource;

import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;

/** Removes only stale transient archive/incomplete uploads that have no managed-file registration. */
public final class OrphanUploadCleanupService {
    private final Path uploadRoot;
    private final ManagedInputFileRepository managedFiles;
    private final Predicate<Path> activeUploadPath;
    private final Duration ttl;

    public OrphanUploadCleanupService(Path uploadRoot, ManagedInputFileRepository managedFiles,
                                      Predicate<Path> activeUploadPath, Duration ttl) {
        this.uploadRoot = Objects.requireNonNull(uploadRoot, "uploadRoot").toAbsolutePath().normalize();
        this.managedFiles = Objects.requireNonNull(managedFiles, "managedFiles");
        this.activeUploadPath = Objects.requireNonNull(activeUploadPath, "activeUploadPath");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.compareTo(Duration.ofHours(24)) < 0) throw new IllegalArgumentException("Orphan upload TTL must be at least 24 hours.");
    }

    public int scanAndCleanup(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!Files.isDirectory(uploadRoot)) return 0;
        int[] count = {0};
        try (var children = Files.list(uploadRoot)) {
            children.filter(this::isTransientUpload).forEach(path -> {
                try {
                    if (path.getFileName().toString().equals(".multisource-archive")) {
                        try (var sessions = Files.list(path)) {
                            var candidates = sessions.toList();
                            if (candidates.isEmpty()) {
                                if (cleanupCandidate(path, now)) count[0]++;
                            } else {
                                candidates.forEach(candidate -> { if (cleanupCandidate(candidate, now)) count[0]++; });
                            }
                        }
                    } else if (cleanupCandidate(path, now)) count[0]++;
                } catch (IOException ignored) { /* A later scheduled scan retries safely. */ }
            });
        } catch (IOException ignored) { }
        return count[0];
    }

    private boolean cleanupCandidate(Path path, Instant now) {
        try {
            Path canonical = path.toAbsolutePath().normalize();
            if (!canonical.startsWith(uploadRoot) || activeUploadPath.test(canonical)
                || managedFiles.findByPath(canonical).isPresent() || !isExpired(canonical, now)) return false;
            deleteTree(canonical); return true;
        } catch (IOException ignored) { return false; }
    }

    private boolean isTransientUpload(Path path) {
        String name = path.getFileName().toString();
        return name.equals(".multisource-archive") || name.endsWith(".part") || name.endsWith(".uploading") || name.endsWith(".incomplete");
    }
    private boolean isExpired(Path path, Instant now) throws IOException {
        FileTime changed = Files.getLastModifiedTime(path);
        return !changed.toInstant().isAfter(now.minus(ttl));
    }
    private static void deleteTree(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { throw new DeleteFailure(exception); }
            });
        } catch (DeleteFailure failure) { throw (IOException) failure.getCause(); }
    }
    private static final class DeleteFailure extends RuntimeException { DeleteFailure(IOException cause) { super(cause); } }
}
