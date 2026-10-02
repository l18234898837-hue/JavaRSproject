package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.TaskId;

import java.time.Instant;
import java.util.List;

public interface TaskInputReferenceRepository {
    void findOrRegister(TaskId taskId, long managedFileId, String sourceType, String sourceId, String checksum);
    List<Long> findActiveReferences(long managedFileId);
    default List<Long> findActiveManagedFiles(TaskId taskId) { return List.of(); }
    void releaseForTask(TaskId taskId, long operatorId, Instant now);
}
