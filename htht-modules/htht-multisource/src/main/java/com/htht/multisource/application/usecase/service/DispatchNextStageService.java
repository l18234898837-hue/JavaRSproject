package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ArtifactValidator;
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
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageExecution;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import com.htht.multisource.infrastructure.worker.WorkerFailurePresentation;
import com.htht.multisource.infrastructure.worker.WorkerProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DispatchNextStageService implements DispatchNextStageUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DispatchNextStageService.class);

    private final ProcessingTaskRepository taskRepository;
    private final TaskStageRepository stageRepository;
    private final TaskDispatcher dispatcher;
    private final AssetStorage assetStorage;
    private final ExecutionIdGenerator executionIdGenerator;
    private final ExecutionClock clock;
    private final Duration leaseDuration;
    private final AcceptWorkerEventUseCase eventUseCase;
    private final ArtifactValidator artifactValidator;
    private final StageOutputContractValidator outputContractValidator;
    private final Executor dispatchExecutor;

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
            eventUseCase, (taskId, stageId, executionId, outputUri, candidate) -> { }, Runnable::run);
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
        ArtifactValidator artifactValidator
    ) {
        this(taskRepository, stageRepository, dispatcher, assetStorage, executionIdGenerator, clock, leaseDuration,
            eventUseCase, artifactValidator, Runnable::run);
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
        ArtifactValidator artifactValidator,
        Executor dispatchExecutor
    ) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.assetStorage = Objects.requireNonNull(assetStorage, "assetStorage");
        this.executionIdGenerator = Objects.requireNonNull(executionIdGenerator, "executionIdGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        this.eventUseCase = Objects.requireNonNull(eventUseCase, "eventUseCase");
        this.artifactValidator = Objects.requireNonNull(artifactValidator, "artifactValidator");
        this.dispatchExecutor = Objects.requireNonNull(dispatchExecutor, "dispatchExecutor");
        this.outputContractValidator = new StageOutputContractValidator();
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
            boolean reserved = false;
            try {
                dispatcher.reserve(executionId);
                reserved = true;
                dispatchExecutor.execute(() -> executeClaim(task, claim.stage(), planStage, candidate, executionId));
            } catch (RuntimeException schedulingFailure) {
                if (reserved) {
                    dispatcher.release(executionId);
                }
                recordFailure(task.id(), claim.stage(), executionId, schedulingFailure);
            }
            return new DispatchResult(claim.stage().id().value(), executionId.value());
        });
    }

    private void executeClaim(ProcessingTask task,
                              TaskStage stage,
                              ProcessingStage planStage,
                              ProductCandidate candidate,
                              ExecutionId executionId) {
        try {
            if (!isCurrentExecution(stage, executionId)) {
                LOG.info(
                    "Skipping queued multisource stage execution because it is no longer current: "
                        + "taskId={}, stageId={}, executionId={}, attempt={}",
                    task.id().value(), stage.id().value(), executionId.value(), stage.attempt()
                );
                return;
            }
            DispatchReceipt receipt = dispatchWithRefresh(() -> {
                var grant = assetStorage.grant(new ExecutionGrantRequest(
                    task.id(), stage.id(), executionId, stage.attempt(), candidate.files(), task.outputUri()
                ));
                return new StageExecutionRequest(
                    task.id(), stage.id(), planStage.stageKey(), stage.attempt(), executionId,
                    "1.0", candidate.profileKey().productCode(), candidate.profileKey().profileVersion(),
                    task.plan().processorVersion(), task.parameterSnapshot(), candidate.files(), task.outputUri(), grant
                );
            });
            outputContractValidator.validate(planStage, task.outputUri(), receipt.artifacts());
            for (var artifact : receipt.artifacts()) {
                artifactValidator.validate(task.id(), stage.id(), executionId, task.outputUri(), artifact);
            }

            eventUseCase.accept(new ExecutionEvent(
                task.id(), stage.id(), executionId, stage.attempt(), 1,
                ExecutionEvent.Type.SUCCEEDED, "{\"workerId\":\"" + escapeJson(receipt.workerId()) + "\"}",
                clock.now(), ExecutionEvent.Acceptance.APPLIED
            ), receipt.artifacts());
        } catch (CancellationException canceled) {
            LOG.info(
                "Multisource stage execution was canceled before completion: "
                    + "taskId={}, stageId={}, executionId={}, attempt={}",
                task.id().value(), stage.id().value(), executionId.value(), stage.attempt()
            );
            return;
        } catch (RuntimeException failure) {
            recordFailure(task.id(), stage, executionId, failure);
            return;
        } finally {
            dispatcher.release(executionId);
        }
    }

    private boolean isCurrentExecution(TaskStage claimedStage, ExecutionId executionId) {
        return stageRepository.findById(claimedStage.id())
            .filter(current -> current.status() == StageExecutionStatus.RUNNING)
            .filter(current -> current.attempt() == claimedStage.attempt())
            .map(TaskStage::execution)
            .filter(Objects::nonNull)
            .map(StageExecution::executionId)
            .filter(executionId::equals)
            .isPresent();
    }

    private void recordFailure(TaskId taskId, TaskStage stage,
                               ExecutionId executionId,
                               RuntimeException failure) {
        String workerRequestId = failure instanceof WorkerProtocolException worker
            ? worker.requestId() : null;
        LOG.error(
            "Multisource stage execution failed: taskId={}, stageId={}, executionId={}, attempt={}, workerRequestId={}",
            taskId.value(), stage.id().value(), executionId.value(), stage.attempt(), workerRequestId, failure
        );
        WorkerFailurePresentation.SafeFailure safeFailure = failure instanceof WorkerProtocolException worker
            ? WorkerFailurePresentation.forClient(worker.code(), worker.workerMessage())
            : WorkerFailurePresentation.forClient(null, null);
        eventUseCase.accept(new ExecutionEvent(
            taskId, stage.id(), executionId, stage.attempt(), 1,
            ExecutionEvent.Type.FAILED,
            "{\"code\":\"" + escapeJson(safeFailure.code()) + "\",\"message\":\""
                + escapeJson(safeFailure.message()) + "\"}",
            clock.now(), ExecutionEvent.Acceptance.APPLIED
        ));
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
