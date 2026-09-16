package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.common.helper.LoginHelper;
import com.htht.multisource.application.port.out.ExecutionClock;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.multisource.application.port.out.TaskStageRepository;
import com.htht.multisource.application.usecase.service.LegacyPlanArchiveService;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.TaskId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Explicit maintenance entry point for retiring plan-v1/v2 tasks.
 * It is deliberately separate from normal task APIs and requires the admin
 * subject, so a client cannot accidentally archive a task during processing.
 */
@RestController
@RequestMapping("/api/multisource/maintenance/legacy-plan-archive")
public final class LegacyPlanMaintenanceController {
    private final ProcessingTaskRepository taskRepository;
    private final TaskStageRepository stageRepository;
    private final LegacyPlanArchiveService archiveService;
    private final ExecutionClock clock;

    public LegacyPlanMaintenanceController(ProcessingTaskRepository taskRepository,
                                            TaskStageRepository stageRepository,
                                            LegacyPlanArchiveService archiveService,
                                            ExecutionClock clock) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.stageRepository = Objects.requireNonNull(stageRepository, "stageRepository");
        this.archiveService = Objects.requireNonNull(archiveService, "archiveService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @PostMapping
    public ResponseEntity<R<MaintenanceResponse>> archive(@RequestBody MaintenanceRequest request) {
        if (!LoginHelper.isAdmin()) {
            throw new SecurityException("Administrator permission is required.");
        }
        if (request == null || request.taskIds() == null || request.taskIds().isEmpty()) {
            throw new IllegalArgumentException("At least one taskId is required.");
        }
        if (request.dryRun() == request.apply()) {
            throw new IllegalArgumentException("Exactly one of dryRun or apply must be true.");
        }
        if (request.apply() && (request.reason() == null || request.reason().isBlank())) {
            throw new IllegalArgumentException("An explicit archive reason is required when apply=true.");
        }

        Instant now = clock.now();
        var rows = request.taskIds().stream().distinct().map(id -> inspect(id, now)).toList();
        if (request.dryRun()) {
            return ResponseEntity.ok(R.ok(new MaintenanceResponse(false, rows, List.of())));
        }
        if (rows.stream().anyMatch(MaintenanceRow::hasNonExpiredLease)) {
            throw new IllegalStateException("A legacy task has a non-expired stage lease; stop workers before apply.");
        }
        var archived = request.taskIds().stream().distinct()
            .map(id -> archiveService.archive(new TaskId(id), request.reason().trim()) ? id : null)
            .filter(Objects::nonNull).toList();
        return ResponseEntity.ok(R.ok(new MaintenanceResponse(true, rows, archived)));
    }

    private MaintenanceRow inspect(long id, Instant now) {
        var task = taskRepository.findById(new TaskId(id))
            .orElseThrow(() -> new IllegalArgumentException("Processing task not found: " + id));
        var stages = stageRepository.findByTaskId(task.id());
        var stageRows = stages.stream().map(stage -> new StageRow(stage.stageKey(), stage.status().name(),
            stage.execution() == null ? null : stage.execution().leaseExpiresAt(),
            stage.status() == StageExecutionStatus.RUNNING && stage.execution() != null
                && stage.execution().leaseExpiresAt().isAfter(now))).toList();
        return new MaintenanceRow(id, task.plan().version(), task.lifecycleStatus().name(), stageRows,
            stageRows.stream().anyMatch(StageRow::hasNonExpiredLease));
    }

    public record MaintenanceRequest(List<Long> taskIds, boolean dryRun, boolean apply, String reason) { }

    public record MaintenanceResponse(boolean applied, List<MaintenanceRow> tasks, List<Long> archivedTaskIds) { }

    public record MaintenanceRow(long taskId, String planVersion, String lifecycleStatus,
                                 List<StageRow> stages, boolean hasNonExpiredLease) { }

    public record StageRow(String stageKey, String executionStatus, Instant leaseExpiresAt,
                           boolean hasNonExpiredLease) { }
}
