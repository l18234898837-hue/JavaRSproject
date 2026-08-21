package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.ProcessingBatchRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingTaskEntity;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingTaskMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskInputAssetMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.TaskProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MyBatisProcessingTaskRepository implements ProcessingTaskRepository {

    private final ProcessingTaskMyBatisMapper taskMapper;
    private final TaskProductMyBatisMapper productMapper;
    private final TaskInputAssetMyBatisMapper inputAssetMapper;
    private final ProcessingBatchRepository batchRepository;
    private final TaskPersistenceMapper persistenceMapper;

    public MyBatisProcessingTaskRepository(ProcessingTaskMyBatisMapper taskMapper, TaskProductMyBatisMapper productMapper,
                                           TaskInputAssetMyBatisMapper inputAssetMapper, ProcessingBatchRepository batchRepository,
                                           TaskPersistenceMapper persistenceMapper) {
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper");
        this.productMapper = Objects.requireNonNull(productMapper, "productMapper");
        this.inputAssetMapper = Objects.requireNonNull(inputAssetMapper, "inputAssetMapper");
        this.batchRepository = Objects.requireNonNull(batchRepository, "batchRepository");
        this.persistenceMapper = Objects.requireNonNull(persistenceMapper, "persistenceMapper");
    }

    @Override
    public Optional<ProcessingTask> findById(TaskId taskId) {
        ProcessingTaskEntity entity = taskMapper.selectById(taskId.value());
        if (entity == null) {
            return Optional.empty();
        }
        return batchRepository.findById(entity.getBatchId()).map(batch -> {
            var assets = inputAssetMapper.selectByTaskId(taskId.value());
            List<ProductCandidate> candidates = productMapper.selectByTaskId(taskId.value()).stream()
                .map(product -> persistenceMapper.toCandidate(product, assets.stream()
                    .filter(asset -> product.getPackageId().equals(asset.getPackageId())).toList()))
                .toList();
            return persistenceMapper.toTask(entity, batch, candidates);
        });
    }

    @Override
    public void save(ProcessingTask task) {
        taskMapper.insert(persistenceMapper.toEntity(task));
        persistenceMapper.toProductEntities(task).forEach(productMapper::insert);
        persistenceMapper.toInputAssetEntities(task).forEach(inputAssetMapper::insert);
    }

    @Override
    public void updateLifecycleStatus(TaskId taskId, TaskLifecycleStatus status) {
        if (taskMapper.updateLifecycleStatus(taskId.value(), status.name()) != 1) {
            throw new IllegalStateException("Processing task lifecycle status could not be updated: " + taskId.value());
        }
    }
}
