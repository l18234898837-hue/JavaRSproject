package com.htht.multisource.application.usecase.service;

import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.TaskStage;

import java.util.List;

public final class TaskLifecycleSummaryService {

    public TaskLifecycleStatus summarize(TaskLifecycleStatus current, List<TaskStage> stages) {
        if (stages.stream().anyMatch(stage -> stage.status() == StageExecutionStatus.FAILED)) {
            return TaskLifecycleStatus.FAILED;
        }
        if (!stages.isEmpty() && stages.stream().allMatch(stage -> stage.status() == StageExecutionStatus.SUCCEEDED)) {
            return TaskLifecycleStatus.SUCCEEDED;
        }
        if (stages.stream().anyMatch(stage -> stage.status() == StageExecutionStatus.RUNNING
            || stage.status() == StageExecutionStatus.PENDING || stage.status() == StageExecutionStatus.RETRYING)) {
            return TaskLifecycleStatus.RUNNING;
        }
        return current;
    }
}
