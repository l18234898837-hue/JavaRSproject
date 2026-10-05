package com.htht.config.multisource;

import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        int[] count = {0};
        scan(now).candidates().forEach(path -> { try { deleteTree(path); count[0]++; } catch (IOException ignored) { } });
        return count[0];
    }

    public ScanResult scan(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!Files.isDirectory(uploadRoot)) return new ScanResult(List.of(), 0L, 0, Map.of(), 0);
        List<Path> candidates = new java.util.ArrayList<>(); Map<String, Integer> skipped = new LinkedHashMap<>();
        long[] bytes = {0L}; int[] failures = {0};
        try (var children = Files.list(uploadRoot)) {
            children.filter(this::isTransientUpload).forEach(path -> {
                try {
                    if (path.getFileName().toString().equals(".multisource-archive")) {
                        try (var sessions = Files.list(path)) {
                            List<Path> entries = sessions.toList();
                            if (entries.isEmpty()) classify(path, now, candidates, skipped, bytes, failures);
                            else entries.forEach(entry -> classify(entry, now, candidates, skipped, bytes, failures));
                        }
                    } else classify(path, now, candidates, skipped, bytes, failures);
                } catch (IOException ignored) { failures[0]++; }
            });
        } catch (IOException ignored) { failures[0]++; }
        return new ScanResult(List.copyOf(candidates), bytes[0], 0, Map.copyOf(skipped), failures[0]);
    }

    private void classify(Path path, Instant now, List<Path> candidates, Map<String, Integer> skipped,
                          long[] bytes, int[] failures) {
        try {
            Path canonical = path.toAbsolutePath().normalize();
            if (!canonical.startsWith(uploadRoot)) { skipped.merge("outside-managed-upload-root", 1, Integer::sum); return; }
            if (activeUploadPath.test(canonical)) { skipped.merge("active-upload-session", 1, Integer::sum); return; }
            if (managedFiles.findByPath(canonical).isPresent()) { skipped.merge("registered-managed-file", 1, Integer::sum); return; }
            if (!isExpired(canonical, now)) { skipped.merge("within-orphan-ttl", 1, Integer::sum); return; }
            candidates.add(canonical); bytes[0] += treeBytes(canonical);
        } catch (IOException ignored) { failures[0]++; }
    }

    private static long treeBytes(Path root) throws IOException {
        try (var files = Files.walk(root)) { return files.filter(Files::isRegularFile).mapToLong(path -> { try { return Files.size(path); } catch (IOException ignored) { return 0L; } }).sum(); }
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
    public record ScanResult(List<Path> candidates, long candidateBytes, int protectedCount, Map<String, Integer> skipped, int failureCount) { }
}
