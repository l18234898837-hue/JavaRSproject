package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.TaskInputRetentionRepository;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskInputRetention;
import com.htht.multisource.domain.model.TaskInputRetentionStatus;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputRetentionEntity;
import com.htht.multisource.infrastructure.persistence.mapper.TaskInputRetentionMyBatisMapper;

import java.util.Objects;
import java.util.Optional;

public final class MyBatisTaskInputRetentionRepository implements TaskInputRetentionRepository {
    private final TaskInputRetentionMyBatisMapper mapper;
    public MyBatisTaskInputRetentionRepository(TaskInputRetentionMyBatisMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }
    @Override public Optional<TaskInputRetention> find(TaskId taskId) { return Optional.ofNullable(mapper.selectById(taskId.value())).map(this::toDomain); }
    @Override public boolean createIfAbsent(TaskInputRetention retention) { return mapper.insertIfAbsent(toEntity(retention)) == 1; }
    @Override public boolean compareAndSet(TaskInputRetention expected, TaskInputRetention next) {
        if (!expected.taskId().equals(next.taskId()) || next.version() != expected.version() + 1)
            throw new IllegalArgumentException("Input retention compare-and-set must advance one version for one task.");
        return mapper.updateIfVersionMatches(expected.version(), toEntity(next)) == 1;
    }
    private TaskInputRetention toDomain(TaskInputRetentionEntity value) {
        return new TaskInputRetention(TaskId.of(value.getTaskId()), value.getWorkspaceKey(),
            TaskInputRetentionStatus.valueOf(value.getRetentionStatus()), value.getExpiresAt(), value.getScheduledAt(),
            value.getPurgeStartedAt(), value.getPurgedAt(), value.getUpdatedAt(), value.getOperatorId(),
            value.getFailureReason(), value.getVersion() == null ? 0L : value.getVersion());
    }
    private static TaskInputRetentionEntity toEntity(TaskInputRetention value) {
        TaskInputRetentionEntity entity = new TaskInputRetentionEntity();
        entity.setTaskId(value.taskId().value()); entity.setWorkspaceKey(value.workspaceKey());
        entity.setRetentionStatus(value.status().name()); entity.setExpiresAt(value.expiresAt());
        entity.setScheduledAt(value.scheduledAt()); entity.setPurgeStartedAt(value.purgeStartedAt());
        entity.setPurgedAt(value.purgedAt()); entity.setUpdatedAt(value.updatedAt()); entity.setOperatorId(value.operatorId());
        entity.setFailureReason(value.failureReason()); entity.setVersion(value.version());
        return entity;
    }
}
