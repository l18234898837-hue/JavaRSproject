package com.htht.multisource.application.port.out;

import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;

import java.util.List;
import java.util.Optional;

public interface TaskStageRepository {

    Optional<TaskStage> findById(StageId stageId);

    List<TaskStage> findByTaskId(TaskId taskId);

    void save(TaskStage stage);
}
