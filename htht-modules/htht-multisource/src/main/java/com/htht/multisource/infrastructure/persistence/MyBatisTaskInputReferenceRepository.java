package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.persistence.mapper.TaskInputReferenceMyBatisMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class MyBatisTaskInputReferenceRepository implements TaskInputReferenceRepository {
    private final TaskInputReferenceMyBatisMapper mapper;
    public MyBatisTaskInputReferenceRepository(TaskInputReferenceMyBatisMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }
    @Override public void findOrRegister(TaskId taskId, long managedFileId, String sourceType, String sourceId, String checksum) {
        mapper.insertActive(taskId.value(), managedFileId, sourceType, sourceId, checksum);
    }
    @Override public List<Long> findActiveReferences(long managedFileId) { return mapper.selectActiveTaskIds(managedFileId); }
    @Override public List<Long> findActiveManagedFiles(TaskId taskId) { return mapper.selectActiveManagedFileIds(taskId.value()); }
    @Override public void releaseForTask(TaskId taskId, long operatorId, Instant now) { mapper.releaseForTask(taskId.value(), operatorId, now); }
}
