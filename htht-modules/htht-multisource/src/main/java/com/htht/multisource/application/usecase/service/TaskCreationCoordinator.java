package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ProcessingPlanner;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.StageIdGenerator;
import com.htht.multisource.application.port.out.TaskIdGenerator;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.PlanRequest;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.infrastructure.worker.WorkerProtocolException;
import com.htht.multisource.domain.model.TaskStage;

import java.util.List;
import java.util.Objects;

public final class TaskCreationCoordinator {

    private final ProcessingTaskRepository taskRepository;
    private final TaskStageRepository taskStageRepository;
    private final ProcessingPlanner planner;
    private final AssetStorage assetStorage;
    private final TaskIdGenerator taskIdGenerator;
    private final StageIdGenerator stageIdGenerator;
    private final String processorVersion;

    public TaskCreationCoordinator(
        ProcessingTaskRepository taskRepository,
        TaskStageRepository taskStageRepository,
        ProcessingPlanner planner,
        AssetStorage assetStorage,
        TaskIdGenerator taskIdGenerator,
        StageIdGenerator stageIdGenerator,
        String processorVersion
    ) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.taskStageRepository = Objects.requireNonNull(taskStageRepository, "taskStageRepository");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.assetStorage = Objects.requireNonNull(assetStorage, "assetStorage");
        this.taskIdGenerator = Objects.requireNonNull(taskIdGenerator, "taskIdGenerator");
        this.stageIdGenerator = Objects.requireNonNull(stageIdGenerator, "stageIdGenerator");
        if (processorVersion == null || processorVersion.isBlank()) {
            throw new IllegalArgumentException("Processor version must not be blank.");
        }
        this.processorVersion = processorVersion;
    }

    public TaskId create(
        ProcessingBatch batch,
        String name,
        AccessSubject subject,
        ProductCandidate candidate,
        String parameterSnapshot,
        StorageUri outputUri
    ) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(parameterSnapshot, "parameterSnapshot");
        Objects.requireNonNull(outputUri, "outputUri");
        if (name.isBlank() || parameterSnapshot.isBlank()) {
            throw new IllegalArgumentException("Processing task creation snapshots are invalid.");
        }
        if (candidate.supportStatus() != ProductSupportStatus.SUPPORTED) {
            throw new IllegalArgumentException("Only supported candidates may create processing tasks.");
        }

        TaskId taskId = taskIdGenerator.nextId();
        ProcessingPlan workerPlan = buildPlanWithFreshGrant(taskId, subject, candidate, parameterSnapshot);
        ProcessingPlan plan = new ProcessingPlan(workerPlan.version(), processorVersion, workerPlan.stages());
        ProcessingTask task = new ProcessingTask(
            taskId, name, subject, TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED,
            parameterSnapshot, outputUri, plan, batch, List.of(candidate)
        );
        taskRepository.save(task);
        for (ProcessingStage stage : plan.stages()) {
            taskStageRepository.save(new TaskStage(
                stageIdGenerator.nextId(), taskId, stage.stageKey(), StageExecutionStatus.PENDING, 1
            ));
        }
        return taskId;
    }

    private ProcessingPlan buildPlanWithFreshGrant(
        TaskId taskId, AccessSubject subject, ProductCandidate candidate, String parameterSnapshot
    ) {
        try {
            return planner.buildPlan(new PlanRequest(
                taskId, candidate, parameterSnapshot, "plan-v3", assetStorage.grantRead(subject, candidate.files())
            ));
        } catch (WorkerProtocolException error) {
            if (!error.isManifestExpired()) {
                throw error;
            }
            return planner.buildPlan(new PlanRequest(
                taskId, candidate, parameterSnapshot, "plan-v3", assetStorage.grantRead(subject, candidate.files())
            ));
        }
    }
}
