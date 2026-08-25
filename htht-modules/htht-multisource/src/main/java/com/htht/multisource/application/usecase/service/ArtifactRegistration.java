package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.ProcessingTask;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;

import java.util.List;

public interface ArtifactRegistration {

    void register(TaskId taskId, StageId stageId, StorageUri outputUri, ExecutionId executionId,
                  List<ArtifactCandidate> candidates);

    default void register(ProcessingTask task, TaskStage stage, ExecutionId executionId,
                          List<ArtifactCandidate> candidates) {
        register(task.id(), stage.id(), task.outputUri(), executionId, candidates);
    }
}
