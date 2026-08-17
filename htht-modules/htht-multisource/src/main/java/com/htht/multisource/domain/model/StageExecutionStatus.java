package com.htht.multisource.domain.model;

public enum StageExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    RETRYING,
    RECOVERY_PENDING
}
