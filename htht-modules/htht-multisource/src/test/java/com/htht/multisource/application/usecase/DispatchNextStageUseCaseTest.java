package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.application.port.out.ExecutionClock;
import com.htht.multisource.application.port.out.ExecutionIdGenerator;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskDispatcher;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.DispatchReceipt;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrantRequest;
import com.htht.multisource.application.port.out.model.StageClaim;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
import com.htht.multisource.application.port.out.model.StageInput;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.application.usecase.service.DispatchNextStageService;
import com.htht.multisource.application.usecase.service.AcceptWorkerEventService;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.infrastructure.worker.WorkerOperation;
import com.htht.multisource.infrastructure.worker.WorkerProtocolException;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingBatchStatus;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageExecution;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@Tag("dev")
class DispatchNextStageUseCaseTest {

    private static final TaskId TASK_ID = TaskId.of(101L);
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final Instant LEASE_EXPIRES_AT = Instant.parse("2026-08-21T09:05:00Z");

    @Test
    void returnsRunningStageBeforeScheduledWorkerExecutionCompletes() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "READ")), Map.of("READ", List.of())
        );
        EmptyRecordingDispatcher dispatcher = new EmptyRecordingDispatcher();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        Executor controlledExecutor = command -> {
            if (!scheduled.compareAndSet(null, command)) {
                throw new IllegalStateException("only one dispatch was expected");
            }
        };
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(task(TaskLifecycleStatus.CREATED)), stages, dispatcher,
            new RecordingAssetStorage(), () -> new ExecutionId("exec-01"), () -> NOW,
            java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository()),
            (taskId, stageId, executionId, outputUri, candidate) -> { }, controlledExecutor
        );

        var result = useCase.dispatch(TASK_ID);

        assertThat(result).contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(dispatcher.request).isNull();
        assertThat(stages.findById(new StageId(201L))).get().extracting(TaskStage::status)
            .isEqualTo(StageExecutionStatus.RUNNING);

        scheduled.get().run();

        assertThat(dispatcher.request).isNotNull();
        assertThat(stages.findById(new StageId(201L))).get().extracting(TaskStage::status)
            .isEqualTo(StageExecutionStatus.SUCCEEDED);
    }

    @Test
    void doesNotStartQueuedWorkerAfterStageWasCanceled() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "READ")), Map.of("READ", List.of())
        );
        EmptyRecordingDispatcher dispatcher = new EmptyRecordingDispatcher();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(task(TaskLifecycleStatus.CREATED)), stages, dispatcher,
            new RecordingAssetStorage(), () -> new ExecutionId("exec-01"), () -> NOW,
            java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository()),
            (taskId, stageId, executionId, outputUri, candidate) -> { }, scheduled::set
        );

        assertThat(useCase.dispatch(TASK_ID))
            .contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        TaskStage running = stages.findById(new StageId(201L)).orElseThrow();
        stages.save(running.cancel(NOW));

        scheduled.get().run();

        assertThat(dispatcher.request).isNull();
        assertThat(stages.findById(new StageId(201L))).get().extracting(TaskStage::status)
            .isEqualTo(StageExecutionStatus.SKIPPED);
    }

    @Test
    void claimsOnlyPendingStageWhoseDependenciesHaveSucceeded() {
        InMemoryStageRepository repository = new InMemoryStageRepository(List.of(
            pending(201L, "READ"), pending(202L, "CAL")
        ), Map.of("READ", List.of(), "CAL", List.of("READ")));

        Optional<StageClaim> first = repository.claimNextRunnable(TASK_ID, execution("exec-01"), NOW);
        Optional<StageClaim> blocked = repository.claimNextRunnable(TASK_ID, execution("exec-02"), NOW);
        repository.complete(new StageId(201L));
        Optional<StageClaim> second = repository.claimNextRunnable(TASK_ID, execution("exec-03"), NOW);

        assertThat(first).map(claim -> claim.stage().stageKey()).contains("READ");
        assertThat(blocked).isEmpty();
        assertThat(second).map(claim -> claim.stage().stageKey()).contains("CAL");
    }

    @Test
    void doesNotClaimStageAlreadyTransitionedToRunning() {
        InMemoryStageRepository repository = new InMemoryStageRepository(
            List.of(pending(201L, "READ")), Map.of("READ", List.of())
        );

        Optional<StageClaim> first = repository.claimNextRunnable(TASK_ID, execution("exec-01"), NOW);
        Optional<StageClaim> duplicate = repository.claimNextRunnable(TASK_ID, execution("exec-02"), NOW);

        assertThat(first).isPresent();
        assertThat(duplicate).isEmpty();
        assertThat(repository.findById(new StageId(201L))).get().extracting(TaskStage::status)
            .isEqualTo(StageExecutionStatus.RUNNING);
    }

    @Test
    void dispatchesClaimedStageWithServerOwnedExecutionGrantAndPlanSnapshot() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "READ")), Map.of("READ", List.of())
        );
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingAssetStorage storage = new RecordingAssetStorage();
        RecordingEventUseCase events = new RecordingEventUseCase(
            new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository(),
                (taskId, stageId, outputUri, executionId, artifacts) -> { },
                new SingleTaskRepository(task(TaskLifecycleStatus.CREATED)))
        );
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(task(TaskLifecycleStatus.CREATED)), stages, dispatcher, storage,
            () -> new ExecutionId("exec-01"), () -> NOW, java.time.Duration.ofMinutes(5),
            events
        );

        var result = useCase.dispatch(TASK_ID);

        assertThat(result).contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(stages.findById(new StageId(201L))).get().satisfies(stage -> {
            assertThat(stage.status()).isEqualTo(StageExecutionStatus.SUCCEEDED);
            assertThat(stage.execution().executionId()).isEqualTo(new ExecutionId("exec-01"));
        });
        assertThat(storage.request).satisfies(request -> {
            assertThat(request.taskId()).isEqualTo(TASK_ID);
            assertThat(request.stageId()).isEqualTo(new StageId(201L));
            assertThat(request.executionId()).isEqualTo(new ExecutionId("exec-01"));
            assertThat(request.attempt()).isEqualTo(1);
            assertThat(request.inputs()).extracting(InputAsset::role).containsExactly("MAIN");
        });
        assertThat(dispatcher.request).satisfies(request -> {
            assertThat(request.stageKey()).isEqualTo("READ");
            assertThat(request.processorVersion()).isEqualTo("worker-1.2.3");
            assertThat(request.executionId()).isEqualTo(new ExecutionId("exec-01"));
            assertThat(request.inputs()).extracting(InputAsset::role).containsExactly("MAIN");
        });
        assertThat(events.artifacts).singleElement().satisfies(artifact ->
            assertThat(artifact.uri().value()).isEqualTo("rs://output/7/task-01/result.tif")
        );
    }

    @Test
    void dispatchesNextRunnableStageAfterSuccessfulCompletion() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "READ"), pending(202L, "WRITE")),
            Map.of("READ", List.of(), "WRITE", List.of("READ"))
        );
        List<String> dispatchedStages = new java.util.ArrayList<>();
        TaskDispatcher dispatcher = new TaskDispatcher() {
            @Override
            public DispatchReceipt dispatch(StageExecutionRequest request) {
                dispatchedStages.add(request.stageKey());
                return new DispatchReceipt("worker-local", true, List.of());
            }

            @Override
            public void cancel(ExecutionId executionId) {
                throw new UnsupportedOperationException();
            }
        };
        AtomicInteger executionSequence = new AtomicInteger();
        SingleTaskRepository tasks = new SingleTaskRepository(twoStageTask());
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            tasks, stages, dispatcher, new RecordingAssetStorage(),
            () -> new ExecutionId("exec-" + executionSequence.incrementAndGet()), () -> NOW,
            java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository(),
                (taskId, stageId, outputUri, executionId, artifacts) -> { }, tasks)
        );

        assertThat(useCase.dispatch(TASK_ID)).isPresent();

        assertThat(dispatchedStages).containsExactly("READ", "WRITE");
        assertThat(stages.findByTaskId(TASK_ID)).extracting(TaskStage::status)
            .containsExactly(StageExecutionStatus.SUCCEEDED, StageExecutionStatus.SUCCEEDED);
    }

    @Test
    void dispatchesDeclaredUpstreamArtifactInsteadOfRawCandidateFiles() {
        TaskStage completedRead = pending(201L, "READ")
            .start(new ExecutionId("exec-read"), null, NOW, LEASE_EXPIRES_AT)
            .succeed(new ExecutionId("exec-read"), 1, NOW);
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(completedRead, pending(202L, "WRITE")),
            Map.of("READ", List.of(), "WRITE", List.of("READ"))
        );
        RecordingAssetStorage storage = new RecordingAssetStorage();
        EmptyRecordingDispatcher dispatcher = new EmptyRecordingDispatcher();
        Artifact upstream = new Artifact(
            TASK_ID, new StageId(201L), "DECODED_RASTER",
            new StorageUri("rs://output/7/task-01/intermediate/decoded.tif"),
            "7/task-01/intermediate/decoded.tif", "sha256:decoded", 128L
        );
        SingleTaskRepository tasks = new SingleTaskRepository(taskWithUpstreamInput());

        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            tasks, stages, dispatcher, storage, () -> new ExecutionId("exec-write"), () -> NOW,
            java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository(),
                (taskId, stageId, outputUri, executionId, artifacts) -> { }, tasks),
            new InMemoryArtifactRepository(List.of(upstream)),
            (taskId, stageId, executionId, outputUri, candidate) -> { }, Runnable::run
        );

        assertThat(useCase.dispatch(TASK_ID)).isPresent();

        assertThat(storage.request.inputs()).singleElement().satisfies(input -> {
            assertThat(input.role()).isEqualTo("decoded");
            assertThat(input.reference()).isEqualTo(new AssetReference(
                "TASK_ARTIFACT", "rs://output/7/task-01/intermediate/decoded.tif"));
            assertThat(input.storageUriSnapshot()).isEqualTo(new StorageUri(
                "rs://task-artifact/7/task-01/intermediate/decoded.tif"));
        });
        assertThat(dispatcher.request.inputs()).containsExactlyElementsOf(storage.request.inputs());
    }

    @Test
    void dispatchesRepeatedRawInputRolesByOrdinal() {
        InputAsset tile0 = new InputAsset("MAIN_TIFF_TILE", 0, new AssetReference("SYS_FILE", "42"),
            new StorageUri("rs://sys-file/42"), "sha256:tile-0", true);
        InputAsset tile1 = new InputAsset("MAIN_TIFF_TILE", 1, new AssetReference("SYS_FILE", "43"),
            new StorageUri("rs://sys-file/43"), "sha256:tile-1", true);
        ProductCandidate candidate = new ProductCandidate("package-01", new ProductProfileKey("GF-6-WFV-L1A", "v1"),
            ProductDataShape.RASTER_GRID, ProductSupportStatus.SUPPORTED, List.of(tile0, tile1), "{}", 0.99);
        ProcessingStage stage = new ProcessingStage(
            "WRITE", "WRITE", List.of(),
            List.of(
                new StageInput("raw-0", StageInput.Source.RAW_INPUT, "MAIN_TIFF_TILE", true),
                new StageInput("raw-1", StageInput.Source.RAW_INPUT, "MAIN_TIFF_TILE", true)
            ),
            List.of(), "{}"
        );
        ProcessingTask task = new ProcessingTask(
            TASK_ID, "task-01", new AccessSubject(7L), TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED,
            "{}", new StorageUri("rs://output/7/task-01"),
            new ProcessingPlan("plan-v3", "worker-1.2.3", List.of(stage)),
            new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate)), List.of(candidate)
        );
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "WRITE")), Map.of("WRITE", List.of())
        );
        RecordingAssetStorage storage = new RecordingAssetStorage();

        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(task), stages, new EmptyRecordingDispatcher(), storage,
            () -> new ExecutionId("exec-01"), () -> NOW, java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository())
        );

        assertThat(useCase.dispatch(TASK_ID)).isPresent();
        assertThat(storage.request.inputs()).extracting(InputAsset::role, InputAsset::ordinal)
            .containsExactly(tuple("MAIN_TIFF_TILE", 0), tuple("MAIN_TIFF_TILE", 1));
    }

    @Test
    void doesNotClaimOrDispatchCancelledTask() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "READ")), Map.of("READ", List.of())
        );
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(task(TaskLifecycleStatus.CANCELED)), stages, dispatcher, new RecordingAssetStorage(),
            () -> new ExecutionId("exec-01"), () -> NOW, java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository())
        );

        assertThat(useCase.dispatch(TASK_ID)).isEmpty();
        assertThat(stages.findById(new StageId(201L))).get().extracting(TaskStage::status)
            .isEqualTo(StageExecutionStatus.PENDING);
        assertThat(dispatcher.request).isNull();
    }

    @Test
    void recordsDispatchFailureAsTerminalEvent() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "READ")), Map.of("READ", List.of())
        );
        InMemoryExecutionEventRepository events = new InMemoryExecutionEventRepository();
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(task(TaskLifecycleStatus.CREATED)), stages, new FailingDispatcher(), new RecordingAssetStorage(),
            () -> new ExecutionId("exec-01"), () -> NOW, java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, events)
        );

        assertThat(useCase.dispatch(TASK_ID)).contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(stages.findById(new StageId(201L))).get().satisfies(stage ->
            assertThat(stage.status()).isEqualTo(StageExecutionStatus.FAILED)
        );
        assertThat(events.events.values()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(com.htht.multisource.application.port.out.model.ExecutionEvent.Type.FAILED);
            assertThat(event.acceptance()).isEqualTo(com.htht.multisource.application.port.out.model.ExecutionEvent.Acceptance.APPLIED);
        });
    }

    @Test
    void recordsInvalidWorkerInputAsTerminalFailureAfterDispatchAcceptance() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "STANDARDIZE_L2A")), Map.of("STANDARDIZE_L2A", List.of())
        );
        InMemoryExecutionEventRepository events = new InMemoryExecutionEventRepository();
        WorkerProtocolException invalidInput = new WorkerProtocolException(
            "exec-01", WorkerOperation.EXECUTE_STAGE,
            "INVALID_REQUEST", "L2A ROI bounds are invalid", false
        );
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(taskWithDeclaredOutput()), stages,
            new TaskDispatcher() {
                @Override
                public DispatchReceipt dispatch(StageExecutionRequest request) {
                    throw invalidInput;
                }

                @Override
                public void cancel(ExecutionId executionId) {
                    throw new UnsupportedOperationException();
                }
            }, new RecordingAssetStorage(), () -> new ExecutionId("exec-01"), () -> NOW,
            java.time.Duration.ofMinutes(5), new AcceptWorkerEventService(stages, events)
        );

        assertThat(useCase.dispatch(TASK_ID))
            .contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(stages.findById(new StageId(201L))).get().satisfies(stage -> {
            assertThat(stage.status()).isEqualTo(StageExecutionStatus.FAILED);
            assertThat(stage.execution().errorCode()).isEqualTo("INVALID_REQUEST");
            assertThat(stage.execution().errorMessage()).isEqualTo("L2A ROI bounds are invalid");
        });
    }

    @Test
    void recordsGf6OutputLimitAsFixedSafeProgressMessage() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "STANDARDIZE_L2A")), Map.of("STANDARDIZE_L2A", List.of())
        );
        WorkerProtocolException outputLimit = new WorkerProtocolException(
            "exec-01", WorkerOperation.EXECUTE_STAGE,
            "GF6_OUTPUT_LIMIT_EXCEEDED", "output grid C:\\secret\\work\\grid.tif is too large", false
        );
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(taskWithDeclaredOutput()), stages, dispatcherThrowing(outputLimit),
            new RecordingAssetStorage(), () -> new ExecutionId("exec-01"), () -> NOW,
            java.time.Duration.ofMinutes(5), new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository())
        );

        assertThat(useCase.dispatch(TASK_ID)).contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(stages.findById(new StageId(201L))).get().satisfies(stage -> {
            assertThat(stage.execution().errorCode()).isEqualTo("GF6_OUTPUT_LIMIT_EXCEEDED");
            assertThat(stage.execution().errorMessage()).isEqualTo(
                "GF6_OUTPUT_LIMIT_EXCEEDED: 输出网格超过服务端安全上限，请增大输出分辨率、缩小 ROI、减少波段或关闭部分衍生产物"
            );
            assertThat(stage.execution().errorMessage()).doesNotContain("C:\\secret");
        });
    }

    @Test
    void recordsUnknownWorkerFailureWithGenericProgressMessage() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "STANDARDIZE_L2A")), Map.of("STANDARDIZE_L2A", List.of())
        );
        WorkerProtocolException workerFailure = new WorkerProtocolException(
            "exec-01", WorkerOperation.EXECUTE_STAGE,
            "UNEXPECTED_WORKER_ERROR", "failed to read C:\\secret\\work\\input.tif", false
        );
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(taskWithDeclaredOutput()), stages, dispatcherThrowing(workerFailure),
            new RecordingAssetStorage(), () -> new ExecutionId("exec-01"), () -> NOW,
            java.time.Duration.ofMinutes(5), new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository())
        );

        assertThat(useCase.dispatch(TASK_ID)).contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(stages.findById(new StageId(201L))).get().satisfies(stage -> {
            assertThat(stage.execution().errorCode()).isEqualTo("WORKER_DISPATCH_FAILED");
            assertThat(stage.execution().errorMessage()).isEqualTo("Worker 执行失败，请稍后重试");
            assertThat(stage.execution().errorMessage()).doesNotContain("C:\\secret");
        });
    }

    @Test
    void recordsWorkerStageTimeoutWithFixedSafeProgressMessage() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "STANDARDIZE_L2A")), Map.of("STANDARDIZE_L2A", List.of())
        );
        WorkerProtocolException timeout = new WorkerProtocolException(
            "worker-request-01", WorkerOperation.EXECUTE_STAGE,
            "WORKER_STAGE_TIMEOUT", "execute-stage process exceeded configured timeout", true
        );
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(taskWithDeclaredOutput()), stages, dispatcherThrowing(timeout),
            new RecordingAssetStorage(), () -> new ExecutionId("exec-01"), () -> NOW,
            java.time.Duration.ofMinutes(5), new AcceptWorkerEventService(stages, new InMemoryExecutionEventRepository())
        );

        assertThat(useCase.dispatch(TASK_ID)).contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(stages.findById(new StageId(201L))).get().satisfies(stage -> {
            assertThat(stage.execution().errorCode()).isEqualTo("WORKER_STAGE_TIMEOUT");
            assertThat(stage.execution().errorMessage()).isEqualTo(
                "处理阶段超过服务端执行时限，请调整输出范围或联系管理员后重试"
            );
            assertThat(stage.execution().errorMessage()).doesNotContain("execute-stage");
        });
    }

    @Test
    void marksStageFailedWhenWorkerOmitsDeclaredOutputArtifacts() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "STANDARDIZE_L2A")), Map.of("STANDARDIZE_L2A", List.of())
        );
        InMemoryExecutionEventRepository events = new InMemoryExecutionEventRepository();
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(taskWithDeclaredOutput()), stages, new EmptyDispatcher(), new RecordingAssetStorage(),
            () -> new ExecutionId("exec-01"), () -> NOW, java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, events)
        );

        assertThat(useCase.dispatch(TASK_ID)).contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(stages.findById(new StageId(201L))).get().extracting(TaskStage::status)
            .isEqualTo(StageExecutionStatus.FAILED);
        assertThat(events.events.values()).singleElement().satisfies(event ->
            assertThat(event.type()).isEqualTo(com.htht.multisource.application.port.out.model.ExecutionEvent.Type.FAILED)
        );
    }

    @Test
    void refreshesExecutionGrantOnceWhenManifestExpires() {
        InMemoryStageRepository stages = new InMemoryStageRepository(
            List.of(pending(201L, "READ")), Map.of("READ", List.of())
        );
        RefreshingDispatcher dispatcher = new RefreshingDispatcher();
        RecordingAssetStorage storage = new RecordingAssetStorage();
        InMemoryExecutionEventRepository eventRepository = new InMemoryExecutionEventRepository();
        DispatchNextStageUseCase useCase = new DispatchNextStageService(
            new SingleTaskRepository(task(TaskLifecycleStatus.CREATED)), stages, dispatcher, storage,
            () -> new ExecutionId("exec-01"), () -> NOW, java.time.Duration.ofMinutes(5),
            new AcceptWorkerEventService(stages, eventRepository)
        );

        assertThat(useCase.dispatch(TASK_ID)).contains(new DispatchNextStageUseCase.DispatchResult(201L, "exec-01"));
        assertThat(storage.grantCalls).isEqualTo(2);
        assertThat(dispatcher.calls).isEqualTo(2);
        assertThat(eventRepository.events.values()).allSatisfy(event ->
            assertThat(event.type()).isNotEqualTo(com.htht.multisource.application.port.out.model.ExecutionEvent.Type.FAILED)
        );
    }

    private static TaskStage pending(long stageId, String stageKey) {
        return new TaskStage(new StageId(stageId), TASK_ID, stageKey, StageExecutionStatus.PENDING, 1);
    }

    private static StageExecution execution(String executionId) {
        return new StageExecution(new ExecutionId(executionId), null, LEASE_EXPIRES_AT, null, null, NOW, null);
    }

    private static ProcessingTask task(TaskLifecycleStatus lifecycleStatus) {
        InputAsset input = new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"),
            new StorageUri("rs://sys-file/42"), "sha256:input", true);
        InputAsset undeclared = new InputAsset("AUXILIARY", 0, new AssetReference("SYS_FILE", "43"),
            new StorageUri("rs://sys-file/43"), "sha256:auxiliary", false);
        ProductCandidate candidate = new ProductCandidate("package-01", new ProductProfileKey("FY-3H-MERSI", "v1"),
            ProductDataShape.SWATH_RASTER, ProductSupportStatus.SUPPORTED, List.of(input, undeclared), "{}", 0.91);
        return new ProcessingTask(TASK_ID, "task-01", new AccessSubject(7L), lifecycleStatus, QualityStatus.NOT_CHECKED,
            "{\"mode\":\"standard\"}", new StorageUri("rs://output/7/task-01"),
            new ProcessingPlan("plan-v1", "worker-1.2.3", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of(),
                List.of(new com.htht.multisource.application.port.out.model.StageInput("main", com.htht.multisource.application.port.out.model.StageInput.Source.RAW_INPUT, "MAIN", true)),
                List.of(), "{}"))),
            new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate)), List.of(candidate));
    }

    private static ProcessingTask taskWithDeclaredOutput() {
        InputAsset input = new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"),
            new StorageUri("rs://sys-file/42"), "sha256:input", true);
        ProductCandidate candidate = new ProductCandidate("package-01", new ProductProfileKey("HY-1C-CZI", "L2A-v1"),
            ProductDataShape.SWATH_RASTER, ProductSupportStatus.SUPPORTED, List.of(input), "{}", 0.91);
        ProcessingStage stage = new ProcessingStage(
            "STANDARDIZE_L2A", "STANDARDIZE_L2A", List.of(), List.of(),
            List.of(new StageOutput("STANDARD_COG", "standard/czi-l2a.tif", StageOutput.Retention.FINAL)), "{}"
        );
        return new ProcessingTask(TASK_ID, "task-01", new AccessSubject(7L), TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED, "{\"mode\":\"standard\"}", new StorageUri("rs://output/7/task-01"),
            new ProcessingPlan("plan-v2", "worker-1.2.3", List.of(stage)),
            new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate)), List.of(candidate));
    }

    private static ProcessingTask twoStageTask() {
        InputAsset input = new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"),
            new StorageUri("rs://sys-file/42"), "sha256:input", true);
        ProductCandidate candidate = new ProductCandidate("package-01", new ProductProfileKey("GENERIC", "v1"),
            ProductDataShape.RASTER_GRID, ProductSupportStatus.SUPPORTED, List.of(input), "{}", 0.91);
        ProcessingStage read = new ProcessingStage("READ", "READ", List.of(), List.of(), List.of(), "{}");
        ProcessingStage write = new ProcessingStage("WRITE", "WRITE", List.of("READ"), List.of(), List.of(), "{}");
        return new ProcessingTask(TASK_ID, "task-01", new AccessSubject(7L), TaskLifecycleStatus.CREATED,
            QualityStatus.NOT_CHECKED, "{}", new StorageUri("rs://output/7/task-01"),
            new ProcessingPlan("plan-v3", "worker-1.2.3", List.of(read, write)),
            new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate)), List.of(candidate));
    }

    private static ProcessingTask taskWithUpstreamInput() {
        InputAsset input = new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"),
            new StorageUri("rs://sys-file/42"), "sha256:input", true);
        ProductCandidate candidate = new ProductCandidate("package-01", new ProductProfileKey("GENERIC", "v1"),
            ProductDataShape.RASTER_GRID, ProductSupportStatus.SUPPORTED, List.of(input), "{}", 0.91);
        ProcessingStage read = new ProcessingStage(
            "READ", "READ", List.of(), List.of(),
            List.of(new StageOutput("DECODED_RASTER", "intermediate/decoded.tif", StageOutput.Retention.INTERMEDIATE)),
            "{}"
        );
        ProcessingStage write = new ProcessingStage(
            "WRITE", "WRITE", List.of("READ"),
            List.of(new StageInput("decoded", StageInput.Source.UPSTREAM_ARTIFACT, "DECODED_RASTER", true)),
            List.of(), "{}"
        );
        return new ProcessingTask(TASK_ID, "task-01", new AccessSubject(7L), TaskLifecycleStatus.RUNNING,
            QualityStatus.NOT_CHECKED, "{}", new StorageUri("rs://output/7/task-01"),
            new ProcessingPlan("plan-v3", "worker-1.2.3", List.of(read, write)),
            new ProcessingBatch(11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate)), List.of(candidate));
    }

    private static TaskDispatcher dispatcherThrowing(RuntimeException failure) {
        return new TaskDispatcher() {
            @Override
            public DispatchReceipt dispatch(StageExecutionRequest request) {
                throw failure;
            }

            @Override
            public void cancel(ExecutionId executionId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class InMemoryStageRepository implements TaskStageRepository {
        private final Map<StageId, TaskStage> stages;
        private final Map<String, List<String>> dependencies;

        private InMemoryStageRepository(List<TaskStage> stages, Map<String, List<String>> dependencies) {
            this.stages = new LinkedHashMap<>();
            stages.forEach(stage -> this.stages.put(stage.id(), stage));
            this.dependencies = dependencies;
        }

        @Override
        public Optional<TaskStage> findById(StageId stageId) {
            return Optional.ofNullable(stages.get(stageId));
        }

        @Override
        public List<TaskStage> findByTaskId(TaskId taskId) {
            return stages.values().stream().filter(stage -> stage.taskId().equals(taskId)).toList();
        }

        @Override
        public void save(TaskStage stage) {
            stages.put(stage.id(), stage);
        }

        @Override
        public Optional<StageClaim> claimNextRunnable(TaskId taskId, StageExecution execution, Instant now) {
            return stages.values().stream()
                .filter(stage -> stage.taskId().equals(taskId) && stage.status() == StageExecutionStatus.PENDING)
                .filter(this::dependenciesSucceeded)
                .findFirst()
                .map(stage -> {
                    TaskStage claimed = stage.start(execution.executionId(), execution.workerId(), now, execution.leaseExpiresAt());
                    stages.put(claimed.id(), claimed);
                    return new StageClaim(claimed);
                });
        }

        private void complete(StageId stageId) {
            TaskStage stage = stages.get(stageId);
            stages.put(stageId, stage.succeed(stage.execution().executionId(), stage.attempt(), NOW));
        }

        private boolean dependenciesSucceeded(TaskStage stage) {
            return dependencies.getOrDefault(stage.stageKey(), List.of()).stream().allMatch(dependency ->
                stages.values().stream().anyMatch(candidate -> candidate.stageKey().equals(dependency)
                    && candidate.status() == StageExecutionStatus.SUCCEEDED)
            );
        }
    }

    private record SingleTaskRepository(ProcessingTask task) implements ProcessingTaskRepository {
        @Override
        public Optional<ProcessingTask> findById(TaskId taskId) {
            return task.id().equals(taskId) ? Optional.of(task) : Optional.empty();
        }

        @Override
        public void save(ProcessingTask task) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryArtifactRepository implements ArtifactRepository {
        private final List<Artifact> artifacts;

        private InMemoryArtifactRepository(List<Artifact> artifacts) {
            this.artifacts = new java.util.ArrayList<>(artifacts);
        }

        @Override
        public List<Artifact> findByTaskId(TaskId taskId) {
            return artifacts.stream().filter(artifact -> artifact.taskId().equals(taskId)).toList();
        }

        @Override
        public void save(Artifact artifact) {
            artifacts.add(artifact);
        }
    }

    private static final class RecordingAssetStorage implements AssetStorage {
        private ExecutionGrantRequest request;
        private int grantCalls;

        @Override
        public ExecutionGrant grant(ExecutionGrantRequest request) {
            grantCalls++;
            this.request = request;
            return new ExecutionGrant("grant-01", LEASE_EXPIRES_AT, request.taskId(), request.stageId(), request.executionId(), request.attempt());
        }

        @Override
        public com.htht.multisource.application.port.out.model.AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.htht.multisource.application.port.out.model.AssetMetadata resolve(AccessSubject subject, AssetReference reference) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RefreshingDispatcher implements TaskDispatcher {
        private int calls;

        @Override
        public DispatchReceipt dispatch(StageExecutionRequest request) {
            calls++;
            if (calls == 1) {
                throw new WorkerProtocolException(
                    "exec-01", WorkerOperation.EXECUTE_STAGE,
                    "INVALID_ASSET_MANIFEST", "manifest is expired", true
                );
            }
            return new DispatchReceipt("worker-local", true, List.of());
        }

        @Override
        public void cancel(ExecutionId executionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingDispatcher implements TaskDispatcher {
        private StageExecutionRequest request;
        @Override
        public DispatchReceipt dispatch(StageExecutionRequest request) {
            this.request = request;
            return new DispatchReceipt("worker-local", true, List.of(new com.htht.multisource.application.port.out.model.ArtifactCandidate(
                new StorageUri("rs://output/7/task-01/result.tif"), "sha256:result", "OUTPUT", 42
            )));
        }

        @Override
        public void cancel(ExecutionId executionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingEventUseCase implements AcceptWorkerEventUseCase {
        private final AcceptWorkerEventUseCase delegate;
        private List<com.htht.multisource.application.port.out.model.ArtifactCandidate> artifacts = List.of();

        private RecordingEventUseCase(AcceptWorkerEventUseCase delegate) {
            this.delegate = delegate;
        }

        @Override
        public Result accept(com.htht.multisource.application.port.out.model.ExecutionEvent event) {
            return delegate.accept(event);
        }

        @Override
        public Result accept(com.htht.multisource.application.port.out.model.ExecutionEvent event,
                             List<com.htht.multisource.application.port.out.model.ArtifactCandidate> artifacts) {
            this.artifacts = artifacts;
            return delegate.accept(event, artifacts);
        }
    }

    private static final class FailingDispatcher implements TaskDispatcher {
        @Override
        public DispatchReceipt dispatch(StageExecutionRequest request) {
            throw new IllegalStateException("Worker process exited with code 1");
        }

        @Override
        public void cancel(ExecutionId executionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptyRecordingDispatcher implements TaskDispatcher {
        private StageExecutionRequest request;

        @Override
        public DispatchReceipt dispatch(StageExecutionRequest request) {
            this.request = request;
            return new DispatchReceipt("worker-local", true, List.of());
        }

        @Override
        public void cancel(ExecutionId executionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptyDispatcher implements TaskDispatcher {
        @Override
        public DispatchReceipt dispatch(StageExecutionRequest request) {
            return new DispatchReceipt("worker-local", true, List.of());
        }

        @Override
        public void cancel(ExecutionId executionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryExecutionEventRepository implements com.htht.multisource.application.port.out.ExecutionEventRepository {
        private final Map<String, com.htht.multisource.application.port.out.model.ExecutionEvent> events = new LinkedHashMap<>();

        @Override
        public boolean insertIfAbsent(com.htht.multisource.application.port.out.model.ExecutionEvent event) {
            return events.putIfAbsent(event.stageId().value() + ":" + event.attempt() + ":" + event.eventSequence(), event) == null;
        }
    }
}
