package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.ProcessingBatch;

import java.util.Optional;

public interface ProcessingBatchRepository {

    Optional<ProcessingBatch> findById(long batchId);

    void save(ProcessingBatch batch);
}
