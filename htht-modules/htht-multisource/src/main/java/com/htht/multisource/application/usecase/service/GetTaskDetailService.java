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
        return new TaskDetailView(
            task.id(), task.batch().id(), task.lifecycleStatus(), task.qualityStatus(), task.plan(), task.candidates(),
            taskStageRepository.findByTaskId(taskId).stream()
                .map(stage -> new TaskStageView(stage.id(), stage.stageKey(), stage.status(), stage.attempt()))
                .toList(),
            artifactRepository.findByTaskId(taskId).stream()
                .map(artifact -> new TaskArtifactView(
                    artifact.stageId(), artifact.type(), artifact.uri(), artifact.checksum()
                ))
                .toList()
        );
    }
}
