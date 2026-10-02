package com.htht.config.multisource;

import java.time.Instant;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/** Separately switchable cleanup jobs. Both are off until rollout is explicitly enabled. */
public final class StorageCleanupScheduler {
    private final OrphanUploadCleanupService orphanUploads;
    private final WorkerDirectoryCleanupService workerDirectories;
    private final boolean sourceEnabled;
    private final boolean orphanEnabled;
    private final boolean workerEnabled;

    public StorageCleanupScheduler(OrphanUploadCleanupService orphanUploads, WorkerDirectoryCleanupService workerDirectories,
                                   boolean sourceEnabled, boolean orphanEnabled, boolean workerEnabled) {
        this.orphanUploads = Objects.requireNonNull(orphanUploads, "orphanUploads");
        this.workerDirectories = Objects.requireNonNull(workerDirectories, "workerDirectories");
        this.sourceEnabled = sourceEnabled; this.orphanEnabled = orphanEnabled; this.workerEnabled = workerEnabled;
    }

    @Scheduled(fixedDelayString = "${multisource.storage-cleanup.interval:1h}")
    public void scan() {
        // Source records are claimed by TaskInputPurgeService. This job only scans transient filesystem data.
        if (orphanEnabled) orphanUploads.scanAndCleanup(Instant.now());
        if (workerEnabled) workerDirectories.scanAndCleanup(Instant.now());
    }
}
