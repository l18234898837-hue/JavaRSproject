package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.application.port.out.ExecutionClock;
import com.htht.multisource.application.port.out.ExecutionIdGenerator;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskDispatcher;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.ExecutionGrantRequest;
import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.application.port.out.model.DispatchReceipt;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
import com.htht.multisource.application.usecase.AcceptWorkerEventUseCase;
import com.htht.multisource.application.usecase.DispatchNextStageUseCase;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.StageExecution;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import com.htht.multisource.infrastructure.worker.WorkerProtocolException;

public final class DispatchNextStageService implements DispatchNextStageUseCase {

    private final ProcessingTaskRepository taskRepository;
    private final TaskStageRepository stageRepository;
    private final TaskDispatcher dispatcher;
    private final AssetStorage assetStorage;
    private final ExecutionIdGenerator executionIdGenerator;
    private final ExecutionClock clock;
    private final Duration leaseDuration;
    private final AcceptWorkerEventUseCase eventUseCase;
    private final ArtifactRepository artifactRepository;
    private final StageInputResolver stageInputResolver;

    public DispatchNextStageService(
        ProcessingTaskRepository taskRepository,
        TaskStageRepository stageRepository,
        TaskDispatcher dispatcher,
        AssetStorage assetStorage,
        ExecutionIdGenerator executionIdGenerator,
        ExecutionClock clock,
        Duration leaseDuration,
        AcceptWorkerEventUseCase eventUseCase
    ) {
        this(taskRepository, stageRepository, dispatcher, assetStorage, executionIdGenerator, clock, leaseDuration,
            eventUseCase, new ArtifactRepository() {
                @Override public java.util.List<com.htht.multisource.domain.model.Artifact> findByTaskId(TaskId taskId) { return java.util.List.of(); }
                @Override public void save(com.htht.multisource.domain.model.Artifact artifact) { throw new UnsupportedOperationException(); }
            }, new StageInputResolver());
    }

    public DispatchNextStageService(
        ProcessingTaskRepository taskRepository,
        TaskStageRepository stageRepository,
        TaskDispatcher dispatcher,
        AssetStorage assetStorage,
        ExecutionIdGenerator executionIdGenerator,
        ExecutionClock clock,
        Duration leaseDuration,
        AcceptWorkerEventUseCase eventUseCase,
        ArtifactRepository artifactRepository,
        StageInputResolver stageInputResolver
    ) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.assetStorage = Objects.requireNonNull(assetStorage, "assetStorage");
        this.executionIdGenerator = Objects.requireNonNull(executionIdGenerator, "executionIdGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        this.eventUseCase = Objects.requireNonNull(eventUseCase, "eventUseCase");
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository");
        this.stageInputResolver = Objects.requireNonNull(stageInputResolver, "stageInputResolver");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be positive.");
        }
    }

    @Override
    public Optional<DispatchResult> dispatch(TaskId taskId) {
        var task = taskRepository.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("Processing task not found."));
        if (task.lifecycleStatus() == TaskLifecycleStatus.CANCELING || task.lifecycleStatus() == TaskLifecycleStatus.CANCELED) {
            return Optional.empty();
        }
        Instant now = clock.now();
        var executionId = executionIdGenerator.nextId();
        var execution = new StageExecution(executionId, null, now.plus(leaseDuration), null, null, now, null);
        return stageRepository.claimNextRunnable(taskId, execution, now).map(claim -> {
            taskRepository.updateLifecycleStatus(taskId, TaskLifecycleStatus.RUNNING);
            ProcessingStage planStage = task.plan().stages().stream()
                .filter(stage -> stage.stageKey().equals(claim.stage().stageKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claimed stage is absent from task plan snapshot."));
            var candidate = task.candidates().get(0);
            var stageInputs = stageInputResolver.resolve(task, planStage, stageRepository.findByTaskId(taskId),
                artifactRepository.findByTaskId(taskId));
            DispatchReceipt receipt;
            try {
                receipt = dispatchWithRefresh(() -> {
                    var grant = assetStorage.grant(new ExecutionGrantRequest(
                        task.id(), claim.stage().id(), executionId, claim.stage().attempt(), stageInputs, task.outputUri()
                    ));
                    return new StageExecutionRequest(
                        task.id(), claim.stage().id(), planStage.stageKey(), claim.stage().attempt(), executionId,
                        "1.0", candidate.profileKey().productCode(), candidate.profileKey().profileVersion(),
                        task.plan().processorVersion(), task.parameterSnapshot(), stageInputs, task.outputUri(), grant
                    );
                });
            } catch (RuntimeException failure) {
                eventUseCase.accept(new ExecutionEvent(
                    task.id(), claim.stage().id(), executionId, claim.stage().attempt(), 1,
                    ExecutionEvent.Type.FAILED,
                    "{\"code\":\"WORKER_DISPATCH_FAILED\",\"message\":\"" + escapeJson(failure.getMessage()) + "\"}",
                    clock.now(), ExecutionEvent.Acceptance.APPLIED
                ));
                return new DispatchResult(claim.stage().id().value(), executionId.value());
            }
            eventUseCase.accept(new ExecutionEvent(
                task.id(), claim.stage().id(), executionId, claim.stage().attempt(), 1,
                ExecutionEvent.Type.SUCCEEDED, "{\"workerId\":\"" + escapeJson(receipt.workerId()) + "\"}",
                clock.now(), ExecutionEvent.Acceptance.APPLIED
            ), receipt.artifacts());
            return new DispatchResult(claim.stage().id().value(), executionId.value());
        });
    }

    private DispatchReceipt dispatchWithRefresh(Supplier<StageExecutionRequest> requestFactory) {
        try {
            return dispatcher.dispatch(requestFactory.get());
        } catch (WorkerProtocolException error) {
            if (!error.isManifestExpired()) {
                throw error;
            }
            return dispatcher.dispatch(requestFactory.get());
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n");
    }
}
