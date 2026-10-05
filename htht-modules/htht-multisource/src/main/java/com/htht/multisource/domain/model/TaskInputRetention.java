package com.htht.multisource.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Immutable retention state and its audit/version coordinates. */
public record TaskInputRetention(TaskId taskId, String workspaceKey,
                                 TaskInputRetentionStatus status, Instant expiresAt,
                                 Instant scheduledAt, Instant purgeStartedAt,
                                 Instant purgedAt, Instant recycledAt,
                                 Instant recycleExpiresAt, Instant updatedAt,
                                 Long operatorId, String failureReason, long version) {
    public TaskInputRetention(TaskId taskId, String workspaceKey,
                               TaskInputRetentionStatus status, Instant expiresAt,
                               Instant scheduledAt, Instant purgeStartedAt,
                               Instant purgedAt, Instant updatedAt,
                               Long operatorId, String failureReason, long version) {
        this(taskId, workspaceKey, status, expiresAt, scheduledAt, purgeStartedAt,
            purgedAt, null, null, updatedAt, operatorId, failureReason, version);
    }

    public enum PolicyChoice { DEFAULT, PERMANENT }
    public TaskInputRetention {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(workspaceKey, "workspaceKey");
        if (workspaceKey.isBlank()) throw new IllegalArgumentException("Workspace key is required.");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) throw new IllegalArgumentException("Retention version must not be negative.");
        if (status == TaskInputRetentionStatus.SCHEDULED && scheduledAt == null)
            throw new IllegalArgumentException("Scheduled retention requires scheduledAt.");
        if (status == TaskInputRetentionStatus.PURGING && purgeStartedAt == null)
            throw new IllegalArgumentException("Purging retention requires purgeStartedAt.");
        if (status == TaskInputRetentionStatus.PURGED && purgedAt == null)
            throw new IllegalArgumentException("Purged retention requires purgedAt.");
        if (status == TaskInputRetentionStatus.RECYCLE_BIN && recycledAt == null)
            throw new IllegalArgumentException("Recycle-bin retention requires recycledAt.");
        if (status == TaskInputRetentionStatus.RECYCLE_BIN && recycleExpiresAt == null)
            throw new IllegalArgumentException("Recycle-bin retention requires recycleExpiresAt.");
    }

    public static TaskInputRetention available(TaskId taskId, String workspaceKey, Instant now) {
        return new TaskInputRetention(taskId, workspaceKey, TaskInputRetentionStatus.AVAILABLE,
            null, null, null, null, null, null, Objects.requireNonNull(now, "now"), null, null, 0L);
    }

    public TaskInputRetention schedule(Instant expiry, AccessSubject operator, Instant now) {
        require(operator, now);
        if (status == TaskInputRetentionStatus.PURGED) throw illegal("schedule");
        if (status != TaskInputRetentionStatus.AVAILABLE && status != TaskInputRetentionStatus.MISSING
            && status != TaskInputRetentionStatus.SCHEDULED)
            throw illegal("schedule");
        if (expiry == null) throw new IllegalArgumentException("Expiry is required for scheduled retention.");
        if (expiry.isBefore(now)) throw new IllegalArgumentException("Expiry must not be in the past.");
        return copy(TaskInputRetentionStatus.SCHEDULED, expiry, now, null, null, null, null, now, operator.userId(), null);
    }

    public TaskInputRetention pin(AccessSubject operator, Instant now) {
        require(operator, now);
        if (status == TaskInputRetentionStatus.PURGED) throw illegal("pin");
        if (status != TaskInputRetentionStatus.AVAILABLE && status != TaskInputRetentionStatus.SCHEDULED)
            throw illegal("pin");
        return copy(TaskInputRetentionStatus.AVAILABLE, null, null, null, null, null, null, now, operator.userId(), null);
    }

    public TaskInputRetention recycle(AccessSubject operator, Instant now) {
        return recycle(operator, now, now.plusSeconds(14L * 24 * 60 * 60));
    }

    public TaskInputRetention recycle(AccessSubject operator, Instant now, Instant expiry) {
        require(operator, now);
        if (status == TaskInputRetentionStatus.RECYCLE_BIN) return this;
        if (status != TaskInputRetentionStatus.AVAILABLE && status != TaskInputRetentionStatus.SCHEDULED) {
            throw illegal("recycle");
        }
        if (expiry == null || expiry.isBefore(now)) {
            throw new IllegalArgumentException("Recycle expiry must not be in the past.");
        }
        return copy(TaskInputRetentionStatus.RECYCLE_BIN, expiresAt, scheduledAt, null, null,
            now, expiry, now, operator.userId(), null);
    }

    public TaskInputRetention restore(AccessSubject operator, Instant now) {
        require(operator, now);
        if (status == TaskInputRetentionStatus.AVAILABLE) return this;
        if (status != TaskInputRetentionStatus.RECYCLE_BIN) throw illegal("restore");
        return copy(TaskInputRetentionStatus.AVAILABLE, null, null, null, null,
            null, null, now, operator.userId(), null);
    }

    public TaskInputRetention beginPurge(AccessSubject operator, Instant now) {
        require(operator, now);
        if (status == TaskInputRetentionStatus.PURGING) return this;
        if (status != TaskInputRetentionStatus.RECYCLE_BIN && status != TaskInputRetentionStatus.PURGE_FAILED) {
            throw illegal("begin purge");
        }
        return copy(TaskInputRetentionStatus.PURGING, expiresAt, scheduledAt, now, null,
            recycledAt, recycleExpiresAt, now, operator.userId(), null);
    }

    public TaskInputRetention claim(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == TaskInputRetentionStatus.PURGED) throw illegal("claim");
        if (status != TaskInputRetentionStatus.SCHEDULED && status != TaskInputRetentionStatus.PURGE_FAILED)
            throw illegal("claim");
        if (expiresAt == null) throw new IllegalStateException("Permanently retained input cannot be claimed.");
        if (status == TaskInputRetentionStatus.SCHEDULED && expiresAt.isAfter(now))
            throw new IllegalStateException("Input retention has not expired.");
        return copy(TaskInputRetentionStatus.PURGING, expiresAt, scheduledAt, now, null,
            null, null, now, null, null);
    }

    public TaskInputRetention purgeSucceeded(AccessSubject operator, Instant now) {
        require(operator, now);
        if (status != TaskInputRetentionStatus.PURGING) throw illegal("complete purge");
        return copy(TaskInputRetentionStatus.PURGED, expiresAt, scheduledAt, purgeStartedAt, now,
            null, null, now, operator.userId(), null);
    }

    public TaskInputRetention purgeFailed(AccessSubject operator, Instant now, String reason) {
        require(operator, now);
        if (status != TaskInputRetentionStatus.PURGING) throw illegal("fail purge");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Purge failure reason is required.");
        return copy(TaskInputRetentionStatus.PURGE_FAILED, expiresAt, scheduledAt, purgeStartedAt, null,
            null, null, now, operator.userId(), reason.trim());
    }

    private TaskInputRetention copy(TaskInputRetentionStatus next, Instant expiry, Instant scheduled,
                                    Instant started, Instant purged, Instant recycled,
                                    Instant recycleExpiry, Instant changedAt, Long operator, String reason) {
        return new TaskInputRetention(taskId, workspaceKey, next, expiry, scheduled, started, purged,
            recycled, recycleExpiry, changedAt,
            operator, reason, version + 1);
    }
    private static void require(AccessSubject operator, Instant now) { Objects.requireNonNull(operator, "operator"); Objects.requireNonNull(now, "now"); }
    private IllegalStateException illegal(String op) { return new IllegalStateException("Cannot " + op + " input retention from " + status + "."); }
}
