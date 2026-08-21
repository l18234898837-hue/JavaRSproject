package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.ExecutionEventRepository;
import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.infrastructure.persistence.mapper.TaskEventMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;

import java.util.Objects;

public final class MyBatisExecutionEventRepository implements ExecutionEventRepository {
    private final TaskEventMyBatisMapper eventMapper;
    private final TaskPersistenceMapper persistenceMapper;

    public MyBatisExecutionEventRepository(TaskEventMyBatisMapper eventMapper, TaskPersistenceMapper persistenceMapper) {
        this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper");
        this.persistenceMapper = Objects.requireNonNull(persistenceMapper, "persistenceMapper");
    }

    @Override
    public boolean insertIfAbsent(ExecutionEvent event) {
        return eventMapper.insertIfAbsent(persistenceMapper.toEventEntity(event)) == 1;
    }
}
