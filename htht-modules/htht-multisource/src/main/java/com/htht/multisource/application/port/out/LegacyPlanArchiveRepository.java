package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.TaskId;

import java.time.Instant;
import java.util.Optional;

/** Persistence boundary for the explicit retirement marker of historical plans. */
public interface LegacyPlanArchiveRepository {
    Optional<ArchiveRecord> findByTaskId(TaskId taskId);
    boolean insertIfAbsent(TaskId taskId, String planVersion, String reason, Instant archivedAt);

    record ArchiveRecord(TaskId taskId, String planVersion, Instant archivedAt, String reason) { }
}
