package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ProcessingBatchIdGenerator;
import com.htht.multisource.application.port.out.ProcessingBatchRepository;
import com.htht.multisource.application.usecase.CreateProcessingTaskCommand;
import com.htht.multisource.application.usecase.CreateProcessingTaskUseCase;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingBatchStatus;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.TaskId;

import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public final class CreateProcessingTaskService implements CreateProcessingTaskUseCase {

    private final ProcessingBatchRepository batchRepository;
    private final ProcessingBatchIdGenerator batchIdGenerator;
    private final TaskCreationCoordinator coordinator;

    public CreateProcessingTaskService(
        ProcessingBatchRepository batchRepository,
        ProcessingBatchIdGenerator batchIdGenerator,
        TaskCreationCoordinator coordinator
    ) {
        this.batchRepository = Objects.requireNonNull(batchRepository, "batchRepository");
        this.batchIdGenerator = Objects.requireNonNull(batchIdGenerator, "batchIdGenerator");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public TaskId create(CreateProcessingTaskCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.candidate().supportStatus() != ProductSupportStatus.SUPPORTED) {
            throw new IllegalArgumentException("Only supported candidates may create processing tasks.");
        }
        ProcessingBatch batch = new ProcessingBatch(
            batchIdGenerator.nextId(), command.name(), ProcessingBatchStatus.CREATED, List.of(command.candidate())
        );
        batchRepository.save(batch);
        return coordinator.create(
            batch, command.name(), command.subject(), command.candidate(), command.parameterSnapshot(), command.outputUri()
        );
    }
}
