package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.time.Instant;
import java.util.Objects;

public record TaskHistoryQuery(AccessSubject subject, String productCode, TaskLifecycleStatus lifecycle,
                               Instant completedFrom, Instant completedTo, TaskArtifactRetentionStatus retention,
                               int page, int pageSize) {
    public TaskHistoryQuery {
        Objects.requireNonNull(subject, "subject");
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("History pagination must be between 1 and 100.");
        }
        if (completedFrom != null && completedTo != null && completedFrom.isAfter(completedTo)) {
            throw new IllegalArgumentException("History completion range is invalid.");
        }
    }
}
