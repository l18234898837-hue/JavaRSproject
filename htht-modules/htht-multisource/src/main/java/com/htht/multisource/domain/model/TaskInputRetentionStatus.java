package com.htht.multisource.domain.model;

/** Lifecycle of the source input snapshot retained for a processing task. */
public enum TaskInputRetentionStatus {
    AVAILABLE, SCHEDULED, PURGING, PURGE_FAILED, PURGED, MISSING
}
