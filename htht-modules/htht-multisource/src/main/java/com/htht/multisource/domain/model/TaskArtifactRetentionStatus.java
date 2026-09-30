package com.htht.multisource.domain.model;

/** Lifecycle of all derived artifacts belonging to one processing task. */
public enum TaskArtifactRetentionStatus {
    AVAILABLE,
    PINNED,
    RECYCLE_BIN,
    PURGING,
    PURGE_FAILED,
    PURGED,
    MISSING
}
