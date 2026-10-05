package com.htht.config.multisource;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Separately switchable cleanup jobs. Both are off until rollout is explicitly enabled. */
public final class StorageCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupScheduler.class);
    private final OrphanUploadCleanupService orphanUploads;
    private final WorkerDirectoryCleanupService workerDirectories;
    private final boolean sourceEnabled;
    private final boolean orphanEnabled;
    private final boolean workerEnabled;
    private final boolean dryRun;
    private final Consumer<CleanupRunSummary> observer;

    public StorageCleanupScheduler(OrphanUploadCleanupService orphanUploads, WorkerDirectoryCleanupService workerDirectories,
                                   boolean sourceEnabled, boolean orphanEnabled, boolean workerEnabled) {
        this(orphanUploads, workerDirectories, sourceEnabled, orphanEnabled, workerEnabled, true, summary -> { });
    }

    public StorageCleanupScheduler(OrphanUploadCleanupService orphanUploads, WorkerDirectoryCleanupService workerDirectories,
                                   boolean sourceEnabled, boolean orphanEnabled, boolean workerEnabled, boolean dryRun,
                                   Consumer<CleanupRunSummary> observer) {
        this.orphanUploads = Objects.requireNonNull(orphanUploads, "orphanUploads");
        this.workerDirectories = Objects.requireNonNull(workerDirectories, "workerDirectories");
        this.sourceEnabled = sourceEnabled; this.orphanEnabled = orphanEnabled; this.workerEnabled = workerEnabled;
        this.dryRun = dryRun; this.observer = Objects.requireNonNull(observer, "observer");
    }

    @Scheduled(fixedDelayString = "${multisource.storage-cleanup.interval:1h}")
    public void scan() {
        scan(Instant.now());
    }

    public void scan(Instant now) {
        int candidates = 0, protectedCount = 0, failures = 0;
        long bytes = 0L;
        Map<String, Integer> skipped = new LinkedHashMap<>();
        // Source records are claimed by TaskInputPurgeService. This job only scans transient filesystem data.
        if (orphanEnabled) {
            var orphan = orphanUploads.scan(now);
            candidates += orphan.candidates().size(); bytes += orphan.candidateBytes(); failures += orphan.failureCount();
            merge(skipped, orphan.skipped());
            if (!dryRun) orphanUploads.scanAndCleanup(now);
        }
        if (workerEnabled) {
            var worker = workerDirectories.scan(now);
            for (var record : worker.directories()) {
                if (record.classification().isDeletable()) { candidates++; bytes += bytes(record.directory()); }
                else { protectedCount++; skipped.merge(record.reason(), 1, Integer::sum); }
            }
            failures += worker.failures().size();
            if (!dryRun) workerDirectories.scanAndCleanup(now);
        }
        CleanupRunSummary summary = new CleanupRunSummary("all-configured", dryRun, candidates, bytes, protectedCount, skipped, failures);
        observer.accept(summary);
        log.info("multisource storage cleanup workspace={} dryRun={} candidates={} candidateBytes={} protected={} skipReasons={} failures={}",
            summary.workspace(), summary.dryRun(), summary.candidateCount(), summary.candidateBytes(), summary.protectedCount(), summary.skipReasons(), summary.failureCount());
    }

    private static void merge(Map<String, Integer> target, Map<String, Integer> values) { values.forEach((key, value) -> target.merge(key, value, Integer::sum)); }
    private static long bytes(Path root) {
        try (var files = Files.walk(root)) { return files.filter(Files::isRegularFile).mapToLong(path -> { try { return Files.size(path); } catch (Exception ignored) { return 0L; } }).sum(); }
        catch (Exception ignored) { return 0L; }
    }
    public record CleanupRunSummary(String workspace, boolean dryRun, int candidateCount, long candidateBytes,
                                    int protectedCount, Map<String, Integer> skipReasons, int failureCount) { }
}
