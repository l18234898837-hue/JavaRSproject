package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.ExecutionClock;
import com.htht.multisource.application.port.out.ExecutionEventRepository;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.application.usecase.RecoverTimedOutStageUseCase;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class RecoverTimedOutStageService implements RecoverTimedOutStageUseCase {
    private final TaskStageRepository stageRepository;
    private final ExecutionEventRepository eventRepository;
    private final ProcessingTaskRepository taskRepository;
    private final ExecutionClock clock;

    public RecoverTimedOutStageService(TaskStageRepository stageRepository, ExecutionEventRepository eventRepository,
                                       ProcessingTaskRepository taskRepository, ExecutionClock clock) {
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverExpired() {
        var now = clock.now();
        int recovered = 0;
        Set<com.htht.multisource.domain.model.TaskId> recoveredTaskIds = new HashSet<>();
        for (var stage : stageRepository.findExpiredRunning(now)) {
            var task = taskRepository.findById(stage.taskId())
                .orElseThrow(() -> new IllegalStateException("Processing task not found for expired stage."));
            if (!"plan-v3".equals(task.plan().version())) {
                // Legacy tasks are retired and handled by the archive maintenance
                // flow; the scheduler must not revive them automatically.
                continue;
            }
            var event = new ExecutionEvent(stage.taskId(), stage.id(), stage.execution().executionId(), stage.attempt(),
                1, ExecutionEvent.Type.TIMED_OUT, "{\"code\":\"LEASE_EXPIRED\"}", now,
                ExecutionEvent.Acceptance.APPLIED);
            if (eventRepository.insertIfAbsent(event)) {
                stageRepository.save(stage.recover(stage.execution().executionId(), stage.attempt(), now));
                recoveredTaskIds.add(stage.taskId());
                recovered++;
            }
        }
        recoveredTaskIds.forEach(taskId -> taskRepository.updateLifecycleStatus(taskId, TaskLifecycleStatus.RECOVERY_PENDING));
        return recovered;
    }
}
