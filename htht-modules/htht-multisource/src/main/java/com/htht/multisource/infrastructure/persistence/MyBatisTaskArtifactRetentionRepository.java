package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.TaskArtifactRetentionRepository;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.persistence.entity.TaskArtifactRetentionEntity;
import com.htht.multisource.infrastructure.persistence.mapper.TaskArtifactRetentionMyBatisMapper;

import java.util.Objects;
import java.util.Optional;

public final class MyBatisTaskArtifactRetentionRepository implements TaskArtifactRetentionRepository {
    private final TaskArtifactRetentionMyBatisMapper mapper;

    public MyBatisTaskArtifactRetentionRepository(TaskArtifactRetentionMyBatisMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Optional<TaskArtifactRetention> find(TaskId taskId) {
        return Optional.ofNullable(mapper.selectById(taskId.value())).map(this::toDomain);
    }

    @Override
    public TaskArtifactRetention findOrCreate(TaskId taskId) {
        return find(taskId).orElseGet(() -> {
            mapper.insertAvailableIfAbsent(taskId.value());
            return find(taskId).orElseThrow(() -> new IllegalStateException("Retention record could not be created."));
        });
    }

    @Override
    public boolean createIfAbsent(TaskArtifactRetention retention) {
        return mapper.insertIfAbsent(toEntity(Objects.requireNonNull(retention, "retention"))) == 1;
    }

    @Override
    public boolean compareAndSet(TaskArtifactRetention expected, TaskArtifactRetention next) {
        if (!expected.taskId().equals(next.taskId()) || next.version() != expected.version() + 1) {
            throw new IllegalArgumentException("Retention compare-and-set must advance one version for one task.");
        }
        return mapper.updateIfVersionMatches(expected.version(), toEntity(next)) == 1;
    }

    private TaskArtifactRetention toDomain(TaskArtifactRetentionEntity entity) {
        return new TaskArtifactRetention(TaskId.of(entity.getTaskId()),
            TaskArtifactRetentionStatus.valueOf(entity.getRetentionStatus()), Boolean.TRUE.equals(entity.getPinned()),
            entity.getRecycledAt(), entity.getPurgeStartedAt(), entity.getPurgedAt(), entity.getOperatorId(),
            entity.getFailureReason(), entity.getVersion());
    }

    private TaskArtifactRetentionEntity toEntity(TaskArtifactRetention retention) {
        TaskArtifactRetentionEntity entity = new TaskArtifactRetentionEntity();
        entity.setTaskId(retention.taskId().value());
        entity.setRetentionStatus(retention.status().name());
        entity.setPinned(retention.pinned());
        entity.setRecycledAt(retention.recycledAt());
        entity.setPurgeStartedAt(retention.purgeStartedAt());
        entity.setPurgedAt(retention.purgedAt());
        entity.setOperatorId(retention.operatorId());
        entity.setFailureReason(retention.failureReason());
        entity.setVersion(retention.version());
        return entity;
    }
}
