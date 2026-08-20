package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ProcessingBatchIdGenerator;
import com.htht.multisource.application.port.out.ProcessingBatchRepository;
import com.htht.multisource.application.usecase.CreateProcessingBatchUseCase;
import com.htht.multisource.application.usecase.model.BatchCreateCommand;
import com.htht.multisource.application.usecase.model.BatchCreationResult;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingBatchStatus;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.TaskId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public final class CreateProcessingBatchService implements CreateProcessingBatchUseCase {

    private final ProcessingBatchRepository batchRepository;
    private final ProcessingBatchIdGenerator batchIdGenerator;
    private final TaskCreationCoordinator coordinator;

    public CreateProcessingBatchService(
        ProcessingBatchRepository batchRepository,
        ProcessingBatchIdGenerator batchIdGenerator,
        TaskCreationCoordinator coordinator
    ) {
        this.batchRepository = Objects.requireNonNull(batchRepository, "batchRepository");
        this.batchIdGenerator = Objects.requireNonNull(batchIdGenerator, "batchIdGenerator");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public BatchCreationResult create(BatchCreateCommand command) {
        Objects.requireNonNull(command, "command");
        validateSupported(command.candidates());
        ProcessingBatch batch = new ProcessingBatch(
            batchIdGenerator.nextId(), command.name(), ProcessingBatchStatus.CREATED, command.candidates()
        );
        batchRepository.save(batch);
        List<TaskId> taskIds = new ArrayList<>();
        for (ProductCandidate candidate : command.candidates()) {
            taskIds.add(coordinator.create(
                batch, command.name(), command.subject(), candidate, command.parameterSnapshot(), command.outputUri()
            ));
        }
        return new BatchCreationResult(batch, taskIds);
    }

    private static void validateSupported(List<ProductCandidate> candidates) {
        for (ProductCandidate candidate : candidates) {
            if (candidate.supportStatus() != ProductSupportStatus.SUPPORTED) {
                throw new IllegalArgumentException("Only supported candidates may create processing tasks.");
            }
        }
    }
}
