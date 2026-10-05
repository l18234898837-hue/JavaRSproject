package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.TaskId;

import java.time.Instant;
import java.util.List;

public interface TaskInputReferenceRepository {
    record SourceInputSummary(long inputCount, long sizeBytes) { }

    void findOrRegister(TaskId taskId, long managedFileId, String sourceType, String sourceId, String checksum);
    List<Long> findActiveReferences(long managedFileId);
    default List<Long> findActiveManagedFiles(TaskId taskId) { return List.of(); }
    default SourceInputSummary summarizeActiveInputs(TaskId taskId) { return new SourceInputSummary(0L, 0L); }
    void releaseForTask(TaskId taskId, long operatorId, Instant now);
}
