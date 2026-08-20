package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.infrastructure.persistence.entity.TaskStageEntity;
import com.htht.multisource.infrastructure.persistence.mapper.TaskStageMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisTaskStageRepository implements TaskStageRepository {

    private final TaskStageMyBatisMapper stageMapper;
    private final TaskPersistenceMapper persistenceMapper;

    public MyBatisTaskStageRepository(TaskStageMyBatisMapper stageMapper, TaskPersistenceMapper persistenceMapper) {
        this.stageMapper = Objects.requireNonNull(stageMapper, "stageMapper");
        this.persistenceMapper = Objects.requireNonNull(persistenceMapper, "persistenceMapper");
    }

    @Override
    public Optional<TaskStage> findById(StageId stageId) {
        TaskStageEntity entity = stageMapper.selectById(stageId.value());
        return entity == null ? Optional.empty() : Optional.of(persistenceMapper.toStage(entity));
    }

    @Override
    public List<TaskStage> findByTaskId(TaskId taskId) {
        return stageMapper.selectByTaskId(taskId.value()).stream().map(persistenceMapper::toStage).toList();
    }

    @Override
    public void save(TaskStage stage) {
        stageMapper.insert(persistenceMapper.toStageEntity(stage));
    }
}
