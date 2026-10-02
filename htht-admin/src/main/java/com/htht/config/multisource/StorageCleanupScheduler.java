package com.htht.config.multisource;

import java.time.Instant;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/** Separately switchable cleanup jobs. Both are off until rollout is explicitly enabled. */
public final class StorageCleanupScheduler {
    private final OrphanUploadCleanupService orphanUploads;
    private final boolean sourceEnabled;
    private final boolean orphanEnabled;

    public StorageCleanupScheduler(OrphanUploadCleanupService orphanUploads, boolean sourceEnabled, boolean orphanEnabled) {
        this.orphanUploads = Objects.requireNonNull(orphanUploads, "orphanUploads");
        this.sourceEnabled = sourceEnabled; this.orphanEnabled = orphanEnabled;
    }

    @Scheduled(fixedDelayString = "${multisource.storage-cleanup.interval:1h}")
    public void scan() {
        // Source records are claimed by TaskInputPurgeService. This job only scans transient filesystem data.
        if (orphanEnabled) orphanUploads.scanAndCleanup(Instant.now());
    }
}
