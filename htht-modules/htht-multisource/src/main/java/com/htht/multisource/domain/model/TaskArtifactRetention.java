package com.htht.multisource.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Immutable retention aggregate for every derived artifact of a task. */
public record TaskArtifactRetention(
    TaskId taskId,
    TaskArtifactRetentionStatus status,
    boolean pinned,
    Instant recycledAt,
    Instant recycleExpiresAt,
    Instant purgeStartedAt,
    Instant purgedAt,
    Long operatorId,
    String failureReason,
    long version
) {
    public TaskArtifactRetention(TaskId taskId, TaskArtifactRetentionStatus status, boolean pinned,
                                 Instant recycledAt, Instant purgeStartedAt, Instant purgedAt,
                                 Long operatorId, String failureReason, long version) {
        this(taskId, status, pinned, recycledAt, null, purgeStartedAt, purgedAt,
            operatorId, failureReason, version);
    }

    public TaskArtifactRetention {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
        if (version < 0) {
            throw new IllegalArgumentException("Retention version must not be negative.");
        }
        if (pinned != (status == TaskArtifactRetentionStatus.PINNED)) {
            throw new IllegalArgumentException("Pinned flag must match PINNED status.");
        }
        if (status == TaskArtifactRetentionStatus.RECYCLE_BIN && recycledAt == null) {
            throw new IllegalArgumentException("Recycle-bin retention requires recycledAt.");
        }
        if (status == TaskArtifactRetentionStatus.PURGING && purgeStartedAt == null) {
            throw new IllegalArgumentException("Purging retention requires purgeStartedAt.");
        }
        if (status == TaskArtifactRetentionStatus.PURGED && purgedAt == null) {
            throw new IllegalArgumentException("Purged retention requires purgedAt.");
        }
    }

    public static TaskArtifactRetention available(TaskId taskId) {
        return new TaskArtifactRetention(taskId, TaskArtifactRetentionStatus.AVAILABLE, false,
            null, null, null, null, null, null, 0L);
    }

    public TaskArtifactRetention pin(AccessSubject operator, Instant now) {
        requireOperatorAndTime(operator, now);
        if (status == TaskArtifactRetentionStatus.PINNED) {
            return this;
        }
        requireStatus(TaskArtifactRetentionStatus.AVAILABLE);
        return copy(TaskArtifactRetentionStatus.PINNED, true, null, null, null, null, operator.userId(), null);
    }

    public TaskArtifactRetention recycle(AccessSubject operator, Instant now) {
        return recycle(operator, now, now.plusSeconds(14L * 24 * 60 * 60));
    }

    public TaskArtifactRetention recycle(AccessSubject operator, Instant now, Instant expiry) {
        requireOperatorAndTime(operator, now);
        if (status == TaskArtifactRetentionStatus.RECYCLE_BIN) {
            return this;
        }
        if (status != TaskArtifactRetentionStatus.AVAILABLE && status != TaskArtifactRetentionStatus.PINNED) {
            throw illegalTransition("recycle");
        }
        if (expiry == null || expiry.isBefore(now)) throw new IllegalArgumentException("Recycle expiry must not be in the past.");
        return copy(TaskArtifactRetentionStatus.RECYCLE_BIN, false, now, expiry, null, null, operator.userId(), null);
    }

    public TaskArtifactRetention restore(AccessSubject operator, Instant now) {
        requireOperatorAndTime(operator, now);
        if (status == TaskArtifactRetentionStatus.AVAILABLE) {
            return this;
        }
        requireStatus(TaskArtifactRetentionStatus.RECYCLE_BIN);
        return copy(TaskArtifactRetentionStatus.AVAILABLE, false, null, null, null, null, operator.userId(), null);
    }

    public TaskArtifactRetention beginPurge(AccessSubject operator, Instant now) {
        requireOperatorAndTime(operator, now);
        if (status == TaskArtifactRetentionStatus.PURGING) {
            return this;
        }
        if (status != TaskArtifactRetentionStatus.RECYCLE_BIN && status != TaskArtifactRetentionStatus.PURGE_FAILED) {
            throw illegalTransition("begin purge");
        }
        return copy(TaskArtifactRetentionStatus.PURGING, false, recycledAt, recycleExpiresAt, now, null, operator.userId(), null);
    }

    public TaskArtifactRetention purgeSucceeded(AccessSubject operator, Instant now) {
        requireOperatorAndTime(operator, now);
        requireStatus(TaskArtifactRetentionStatus.PURGING);
        return copy(TaskArtifactRetentionStatus.PURGED, false, recycledAt, recycleExpiresAt, purgeStartedAt, now, operator.userId(), null);
    }

    public TaskArtifactRetention purgeFailed(AccessSubject operator, Instant now, String reason) {
        requireOperatorAndTime(operator, now);
        requireStatus(TaskArtifactRetentionStatus.PURGING);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Purge failure reason is required.");
        }
        return copy(TaskArtifactRetentionStatus.PURGE_FAILED, false, recycledAt, recycleExpiresAt, purgeStartedAt, null,
            operator.userId(), reason.trim());
    }

    public TaskArtifactRetention markMissing(AccessSubject operator, Instant now) {
        requireOperatorAndTime(operator, now);
        if (status == TaskArtifactRetentionStatus.MISSING) {
            return this;
        }
        if (status != TaskArtifactRetentionStatus.AVAILABLE && status != TaskArtifactRetentionStatus.PINNED) {
            throw illegalTransition("mark missing");
        }
        return copy(TaskArtifactRetentionStatus.MISSING, false, null, null, null, null, operator.userId(), null);
    }

    private TaskArtifactRetention copy(TaskArtifactRetentionStatus nextStatus, boolean nextPinned,
                                       Instant nextRecycledAt, Instant nextRecycleExpiresAt,
                                       Instant nextPurgeStartedAt, Instant nextPurgedAt,
                                       Long nextOperatorId, String nextFailureReason) {
        return new TaskArtifactRetention(taskId, nextStatus, nextPinned, nextRecycledAt, nextRecycleExpiresAt,
            nextPurgeStartedAt, nextPurgedAt, nextOperatorId, nextFailureReason, version + 1);
    }

    private void requireStatus(TaskArtifactRetentionStatus expected) {
        if (status != expected) {
            throw illegalTransition(expected.name());
        }
    }

    private IllegalStateException illegalTransition(String operation) {
        return new IllegalStateException("Cannot " + operation + " task artifacts from " + status + ".");
    }

    private static void requireOperatorAndTime(AccessSubject operator, Instant now) {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(now, "now");
    }
}
