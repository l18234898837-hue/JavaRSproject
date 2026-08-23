package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.ProcessingBatchRepository;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingBatchStatus;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingBatchEntity;
import com.htht.multisource.infrastructure.persistence.mapper.BatchProductMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapper.ProcessingBatchMyBatisMapper;
import com.htht.multisource.infrastructure.persistence.mapping.TaskPersistenceMapper;

import java.util.Objects;
import java.util.Optional;

public final class MyBatisProcessingBatchRepository implements ProcessingBatchRepository {

    private final ProcessingBatchMyBatisMapper batchMapper;
    private final BatchProductMyBatisMapper productMapper;
    private final TaskPersistenceMapper persistenceMapper;

    public MyBatisProcessingBatchRepository(ProcessingBatchMyBatisMapper batchMapper, BatchProductMyBatisMapper productMapper,
                                            TaskPersistenceMapper persistenceMapper) {
        this.batchMapper = Objects.requireNonNull(batchMapper, "batchMapper");
        this.productMapper = Objects.requireNonNull(productMapper, "productMapper");
        this.persistenceMapper = Objects.requireNonNull(persistenceMapper, "persistenceMapper");
    }

    @Override
    public Optional<ProcessingBatch> findById(long batchId) {
        ProcessingBatchEntity entity = batchMapper.selectById(batchId);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(new ProcessingBatch(entity.getBatchId(), entity.getName(),
            ProcessingBatchStatus.valueOf(entity.getStatus()), productMapper.selectByBatchId(batchId).stream()
                .map(persistenceMapper::toBatchCandidate).toList()));
    }

    @Override
    public void save(ProcessingBatch batch) {
        batchMapper.insert(persistenceMapper.toBatchEntity(batch));
        batch.candidates().forEach(candidate -> productMapper.insert(persistenceMapper.toBatchProductEntity(batch.id(), candidate)));
    }
}
