package com.htht.multisource.application.port.out;

import java.util.Optional;

/** Workspace policy lookup; adapters may return empty until persistence is wired. */
public interface WorkspaceStoragePolicyRepository {
    Optional<Policy> findByWorkspace(String workspaceKey);

    default Optional<Policy> findDefault() { return findByWorkspace("default"); }

    record Policy(int inputRetentionDays, int orphanUploadTtlHours,
                  int workerDirectoryTtlHours, boolean autoCleanupEnabled) {
        public Policy {
            if (inputRetentionDays < 0 || inputRetentionDays > 3650)
                throw new IllegalArgumentException("Input retention days must be between 0 and 3650.");
            if (orphanUploadTtlHours < 24)
                throw new IllegalArgumentException("Orphan upload TTL must be at least 24 hours.");
            if (workerDirectoryTtlHours < 1)
                throw new IllegalArgumentException("Worker directory TTL must be at least 1 hour.");
        }
        public static Policy defaults() { return new Policy(14, 24, 48, false); }
    }
}
