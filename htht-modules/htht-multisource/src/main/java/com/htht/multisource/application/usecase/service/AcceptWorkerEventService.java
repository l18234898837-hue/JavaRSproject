package com.htht.multisource.application.usecase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.application.port.out.ExecutionEventRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.application.usecase.AcceptWorkerEventUseCase;
import com.htht.multisource.domain.model.StageExecutionStatus;

import java.util.Objects;
import java.util.List;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import org.springframework.transaction.annotation.Transactional;

public class AcceptWorkerEventService implements AcceptWorkerEventUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TaskStageRepository stageRepository;
    private final ExecutionEventRepository eventRepository;
    private final ArtifactRegistration artifactRegistration;
    private final ProcessingTaskRepository taskRepository;

    public AcceptWorkerEventService(TaskStageRepository stageRepository, ExecutionEventRepository eventRepository) {
        this(stageRepository, eventRepository, (taskId, stageId, outputUri, executionId, artifacts) -> { }, null);
    }

    public AcceptWorkerEventService(TaskStageRepository stageRepository, ExecutionEventRepository eventRepository,
                                    ArtifactRegistration artifactRegistration) {
        this(stageRepository, eventRepository, artifactRegistration, null);
    }

    public AcceptWorkerEventService(TaskStageRepository stageRepository, ExecutionEventRepository eventRepository,
                                    ArtifactRegistration artifactRegistration,
                                    ProcessingTaskRepository taskRepository) {
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.artifactRegistration = Objects.requireNonNull(artifactRegistration, "artifactRegistration");
        this.taskRepository = taskRepository;
    }

    @Override
    public Result accept(ExecutionEvent event) {
        return accept(event, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result accept(ExecutionEvent event, List<ArtifactCandidate> artifacts) {
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
            case SUCCEEDED -> {
                if (!artifacts.isEmpty()) {
                    artifactRegistration.register(event.taskId(), event.stageId(),
                        resolveOutputUri(event.taskId()), event.executionId(), artifacts);
                }
                stageRepository.save(stage.succeed(event.executionId(), event.attempt(), event.occurredAt()));
            }
            case FAILED -> {
                FailureDetails failure = failureDetails(event.payload());
                stageRepository.save(stage.fail(
                    event.executionId(), event.attempt(), failure.code(), failure.message(), event.occurredAt()
                ));
            }
            case TIMED_OUT -> stageRepository.save(stage.recover(event.executionId(), event.attempt(), event.occurredAt()));
            case CANCELED -> throw new UnsupportedOperationException("Cancellation event handling is implemented with task cancellation.");
        }
        updateTaskLifecycle(event.taskId());
        return Result.APPLIED;
    }

    private StorageUri resolveOutputUri(TaskId taskId) {
        if (taskRepository == null) {
            throw new IllegalStateException("Task output URI resolver is not configured.");
        }
        return taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Processing task not found."))
            .outputUri();
    }

    private static FailureDetails failureDetails(String payload) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            String code = root == null ? null : root.path("code").asText(null);
            String message = root == null ? null : root.path("message").asText(null);
            if (code != null && !code.isBlank() && message != null && !message.isBlank()) {
                return new FailureDetails(code, message);
            }
        } catch (Exception ignored) {
            // Keep the original event payload in the fallback message below.
        }
        String message = payload == null || payload.isBlank() ? "Worker stage failed." : payload;
        return new FailureDetails("WORKER_FAILED", message);
    }

    private record FailureDetails(String code, String message) {
    }

    private void updateTaskLifecycle(TaskId taskId) {
        if (taskRepository == null) {
            return;
        }
        var task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Processing task not found."));
        taskRepository.updateLifecycleStatus(taskId,
            new TaskLifecycleSummaryService().summarize(task.lifecycleStatus(), stageRepository.findByTaskId(taskId)));
    }
}
