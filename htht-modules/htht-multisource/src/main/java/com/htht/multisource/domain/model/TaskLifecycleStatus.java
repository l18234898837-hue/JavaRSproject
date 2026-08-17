package com.htht.multisource.domain.model;

public enum TaskLifecycleStatus {
    CREATED,
    QUEUED,
    RUNNING,
    RECOVERY_PENDING,
    SUCCEEDED,
    FAILED,
    CANCELING,
    CANCELED
}
