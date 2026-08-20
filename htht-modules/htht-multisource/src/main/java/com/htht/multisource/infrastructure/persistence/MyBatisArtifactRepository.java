package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.persistence.mapper.ArtifactMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;

import java.util.List;
import java.util.Objects;

public final class MyBatisArtifactRepository implements ArtifactRepository {

    private final ArtifactMyBatisMapper artifactMapper;
    private final TaskPersistenceMapper persistenceMapper;

    public MyBatisArtifactRepository(ArtifactMyBatisMapper artifactMapper, TaskPersistenceMapper persistenceMapper) {
        this.artifactMapper = Objects.requireNonNull(artifactMapper, "artifactMapper");
        this.persistenceMapper = Objects.requireNonNull(persistenceMapper, "persistenceMapper");
    }

    @Override
    public List<Artifact> findByTaskId(TaskId taskId) {
        return artifactMapper.selectByTaskId(taskId.value()).stream().map(persistenceMapper::toArtifact).toList();
    }

    @Override
    public void save(Artifact artifact) {
        artifactMapper.insert(persistenceMapper.toArtifactEntity(artifact));
    }
}
