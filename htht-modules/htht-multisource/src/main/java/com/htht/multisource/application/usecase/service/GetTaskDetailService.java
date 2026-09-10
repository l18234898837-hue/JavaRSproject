package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ArtifactRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskStageView;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;

import java.util.NoSuchElementException;
import java.util.Objects;

public final class GetTaskDetailService implements GetTaskDetailUseCase {

    private final ProcessingTaskRepository taskRepository;
    private final TaskStageRepository taskStageRepository;
    private final ArtifactRepository artifactRepository;

    public GetTaskDetailService(
        ProcessingTaskRepository taskRepository,
        TaskStageRepository taskStageRepository,
        ArtifactRepository artifactRepository
    ) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.taskStageRepository = Objects.requireNonNull(taskStageRepository, "taskStageRepository");
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository");
    }

    @Override
    public TaskDetailView get(TaskId taskId, AccessSubject subject) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(subject, "subject");
        var task = taskRepository.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("Processing task not found."));
        if (!task.creator().equals(subject)) {
            throw new SecurityException("Processing task access denied.");
        }
        var stages = taskStageRepository.findByTaskId(taskId);
        return new TaskDetailView(
            task.id(), task.batch().id(), task.lifecycleStatus(), task.qualityStatus(), task.plan(), task.candidates(),
            stages.stream()
                .map(stage -> new TaskStageView(
                    stage.id(), stage.stageKey(), stage.status(), stage.attempt(),
                    stage.execution() == null ? null : stage.execution().executionId().value(),
                    stage.execution() == null ? null : stage.execution().errorCode(),
                    stage.execution() == null ? null : stage.execution().errorMessage(),
                    stage.execution() == null ? null : stage.execution().startedAt(),
                    stage.execution() == null ? null : stage.execution().finishedAt()
                ))
                .toList(),
            artifactRepository.findByTaskId(taskId).stream()
                .map(artifact -> new TaskArtifactView(
                    artifact.stageId(), artifact.type(), artifact.uri(), artifact.checksum(), artifact.sizeBytes(),
                    retention(task, stages, artifact.stageId(), artifact.type()), artifact.storageKey()
                ))
                .toList()
        );
    }

    private static String retention(com.htht.multisource.domain.model.ProcessingTask task,
                                    java.util.List<com.htht.multisource.domain.model.TaskStage> stages,
                                    com.htht.multisource.domain.model.StageId stageId, String artifactType) {
        return stages.stream().filter(stage -> stage.id().equals(stageId)).map(com.htht.multisource.domain.model.TaskStage::stageKey)
            .findFirst().flatMap(stageKey -> task.plan().stages().stream()
                .filter(stage -> stage.stageKey().equals(stageKey)).findFirst())
            .flatMap(stage -> stage.outputs().stream().filter(output -> output.artifactType().equals(artifactType)).findFirst())
            .map(output -> output.retention().name()).orElse(null);
    }

}
