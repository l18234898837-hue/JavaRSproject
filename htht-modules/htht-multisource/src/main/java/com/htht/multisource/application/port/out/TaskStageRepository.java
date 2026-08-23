package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.StageClaim;
import com.htht.multisource.domain.model.StageExecution;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskStage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskStageRepository {

    Optional<TaskStage> findById(StageId stageId);

    List<TaskStage> findByTaskId(TaskId taskId);

    void save(TaskStage stage);

    default List<TaskStage> findExpiredRunning(Instant now) {
        throw new UnsupportedOperationException("Expired stage lookup is not implemented.");
    }

    default Optional<StageClaim> claimNextRunnable(TaskId taskId, StageExecution execution, Instant now) {
        throw new UnsupportedOperationException("Atomic stage claiming is not implemented.");
    }
}
