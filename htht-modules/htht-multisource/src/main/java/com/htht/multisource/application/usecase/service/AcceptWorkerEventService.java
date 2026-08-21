package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ExecutionEventRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.application.usecase.AcceptWorkerEventUseCase;
import com.htht.multisource.domain.model.StageExecutionStatus;

import java.util.Objects;

public final class AcceptWorkerEventService implements AcceptWorkerEventUseCase {

    private final TaskStageRepository stageRepository;
    private final ExecutionEventRepository eventRepository;

    public AcceptWorkerEventService(TaskStageRepository stageRepository, ExecutionEventRepository eventRepository) {
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
    }

    @Override
    public Result accept(ExecutionEvent event) {
        var stage = stageRepository.findById(event.stageId())
            .orElseThrow(() -> new IllegalArgumentException("Processing stage not found."));
        boolean current = stage.status() == StageExecutionStatus.RUNNING
            && stage.execution() != null
            && stage.attempt() == event.attempt()
            && stage.execution().executionId().equals(event.executionId());
        ExecutionEvent persistedEvent = event.withAcceptance(
            current ? ExecutionEvent.Acceptance.APPLIED : ExecutionEvent.Acceptance.IGNORED
        );
        if (!eventRepository.insertIfAbsent(persistedEvent)) {
            return Result.DUPLICATE;
        }
        if (!current) {
            return Result.IGNORED;
        }
        switch (event.type()) {
            case SUCCEEDED -> stageRepository.save(stage.succeed(event.executionId(), event.attempt(), event.occurredAt()));
            case FAILED -> stageRepository.save(stage.fail(
                event.executionId(), event.attempt(), "WORKER_FAILED", event.payload(), event.occurredAt()
            ));
            case TIMED_OUT -> stageRepository.save(stage.recover(event.executionId(), event.attempt(), event.occurredAt()));
            case CANCELED -> throw new UnsupportedOperationException("Cancellation event handling is implemented with task cancellation.");
        }
        return Result.APPLIED;
    }
}
