package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.multisource.application.port.out.TaskArtifactRetentionRepository;
import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.TaskArtifactPurgeUseCase;
import com.htht.multisource.application.usecase.TaskArtifactRetentionUseCase;
import com.htht.multisource.application.usecase.TaskInputPurgeUseCase;
import com.htht.multisource.application.usecase.TaskInputRetentionUseCase;
import com.htht.multisource.application.usecase.TaskHistoryQueryUseCase;
import com.htht.multisource.application.usecase.model.ArtifactRetentionOperation;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.TaskInputRetention;
import com.htht.multisource.domain.model.TaskInputRetentionStatus;
import com.htht.multisource.application.port.out.ProcessingTaskRepository;
import com.htht.web.controller.multisource.dto.ArtifactPurgeRequest;
import com.htht.web.controller.multisource.dto.ArtifactRetentionRequest;
import com.htht.web.controller.multisource.dto.ProcessingStorageSummaryResponse;
import com.htht.web.controller.multisource.dto.TaskHistoryDetailResponse;
import com.htht.web.controller.multisource.dto.TaskHistoryResponse;
import com.htht.web.controller.multisource.dto.TaskInputRetentionRequest;
import com.htht.web.controller.multisource.dto.BatchTaskCleanupRequest;
import com.htht.web.controller.multisource.dto.BatchTaskRetentionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/multisource")
public final class TaskHistoryController {
    private final Supplier<AccessSubject> currentSubject;
    private final TaskHistoryQueryUseCase historyQuery;
    private final GetTaskDetailUseCase detailUseCase;
    private final TaskArtifactRetentionRepository retentionRepository;
    private final TaskArtifactRetentionUseCase retentionUseCase;
    private final TaskArtifactPurgeUseCase purgeUseCase;
    private final TaskInputRetentionUseCase inputRetentionUseCase;
    private final TaskInputPurgeUseCase inputPurgeUseCase;
    private final ProcessingTaskRepository taskRepository;
    private final TaskHistoryRepository historyRepository;
    private final TaskInputReferenceRepository inputReferenceRepository;

    public TaskHistoryController(
        Supplier<AccessSubject> currentSubject,
        TaskHistoryQueryUseCase historyQuery,
        GetTaskDetailUseCase detailUseCase,
        TaskArtifactRetentionRepository retentionRepository,
        TaskArtifactRetentionUseCase retentionUseCase,
        TaskArtifactPurgeUseCase purgeUseCase
    ) {
        this(currentSubject, historyQuery, detailUseCase, retentionRepository, retentionUseCase, purgeUseCase,
            null, null, null, null, null);
    }

    @Autowired
    public TaskHistoryController(
        Supplier<AccessSubject> currentSubject,
        TaskHistoryQueryUseCase historyQuery,
        GetTaskDetailUseCase detailUseCase,
        TaskArtifactRetentionRepository retentionRepository,
        TaskArtifactRetentionUseCase retentionUseCase,
        TaskArtifactPurgeUseCase purgeUseCase,
        TaskInputRetentionUseCase inputRetentionUseCase,
        TaskInputPurgeUseCase inputPurgeUseCase,
        ProcessingTaskRepository taskRepository,
        TaskHistoryRepository historyRepository,
        TaskInputReferenceRepository inputReferenceRepository
    ) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.historyQuery = Objects.requireNonNull(historyQuery, "historyQuery");
        this.detailUseCase = Objects.requireNonNull(detailUseCase, "detailUseCase");
        this.retentionRepository = Objects.requireNonNull(retentionRepository, "retentionRepository");
        this.retentionUseCase = Objects.requireNonNull(retentionUseCase, "retentionUseCase");
        this.purgeUseCase = Objects.requireNonNull(purgeUseCase, "purgeUseCase");
        this.inputRetentionUseCase = inputRetentionUseCase;
        this.inputPurgeUseCase = inputPurgeUseCase;
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.inputReferenceRepository = inputReferenceRepository;
    }

    @GetMapping("/processing-tasks/history")
    public ResponseEntity<R<TaskHistoryResponse>> history(
        @RequestParam(required = false) String productCode,
        @RequestParam(required = false) String lifecycle,
        @RequestParam(required = false) String completedFrom,
        @RequestParam(required = false) String completedTo,
        @RequestParam(required = false) String retention,
        @RequestParam(required = false) String inputRetention,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        AccessSubject subject = currentSubject.get();
        var result = historyQuery.query(new TaskHistoryQuery(subject, blankToNull(productCode),
            enumValue(lifecycle, TaskLifecycleStatus.class, "lifecycle"), parseInstant(completedFrom, "completedFrom"),
            parseInstant(completedTo, "completedTo"), enumValue(retention, TaskArtifactRetentionStatus.class, "retention"), page, pageSize,
            enumValue(inputRetention, TaskInputRetentionStatus.class, "inputRetention")));
        return ResponseEntity.ok(R.ok(new TaskHistoryResponse(result.items(), result.total(), result.page(), result.pageSize())));
    }

    /** Source-compatible overload for existing controller tests and integrations. */
    public ResponseEntity<R<TaskHistoryResponse>> history(String productCode, String lifecycle,
        String completedFrom, String completedTo, String retention, int page, int pageSize) {
        return history(productCode, lifecycle, completedFrom, completedTo, retention, null, page, pageSize);
    }

    @GetMapping("/processing-tasks/{taskId}/history-detail")
    public ResponseEntity<R<TaskHistoryDetailResponse>> detail(@PathVariable long taskId) {
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        TaskArtifactRetention retention = retentionRepository.findOrCreate(detail.id());
        long totalBytes = detail.artifacts().stream().mapToLong(artifact -> Math.max(0L, artifact.sizeBytes())).sum();
        TaskInputRetention source = inputRetentionUseCase == null ? null : inputRetentionUseCase.find(detail.id()).orElse(null);
        var sourceInputs = inputReferenceRepository == null ? new TaskInputReferenceRepository.SourceInputSummary(0L, 0L)
            : inputReferenceRepository.summarizeActiveInputs(detail.id());
        return ResponseEntity.ok(R.ok(new TaskHistoryDetailResponse(detail, retention, totalBytes, source,
            sourceInputs.inputCount(), sourceInputs.sizeBytes())));
    }

    @PostMapping("/processing-tasks/{taskId}/input-retention")
    public ResponseEntity<R<TaskInputRetention>> changeInputRetention(@PathVariable long taskId,
                                                                       @RequestBody TaskInputRetentionRequest request) {
        requireInputRetentionConfigured();
        String operation = blankToNull(request == null ? null : request.operation());
        if (operation == null) throw new IllegalArgumentException("operation is required.");
        TaskId id = TaskId.of(taskId);
        AccessSubject subject = currentSubject.get();
        ensureOwned(id, subject);
        String workspace = workspaceFor(id, subject);
        TaskInputRetention value;
        switch (operation.toUpperCase(Locale.ROOT)) {
            case "PIN" -> value = inputRetentionUseCase.changePolicy(id, workspace, TaskInputRetention.PolicyChoice.PERMANENT, subject);
            case "SET_EXPIRY" -> value = inputRetentionUseCase.schedule(id, workspace, parseInstant(request.expiresAt(), "expiresAt"), subject);
            case "PURGE" -> {
                if (inputPurgeUseCase == null) throw new IllegalStateException("Input purge service is not configured.");
                value = inputPurgeUseCase.request(id, subject, request.taskNo(), workspace);
            }
            default -> throw new IllegalArgumentException("operation is invalid.");
        }
        return ResponseEntity.ok(R.ok(value));
    }

    @PostMapping("/processing-tasks/batch-input-retention")
    public ResponseEntity<R<BatchTaskRetentionResponse>> batchInputRetention(@RequestBody BatchTaskCleanupRequest request) {
        requireInputRetentionConfigured();
        return ResponseEntity.ok(R.ok(batchInput(request)));
    }

    @PostMapping("/processing-tasks/batch-artifact-retention")
    public ResponseEntity<R<BatchTaskRetentionResponse>> batchArtifactRetention(@RequestBody BatchTaskCleanupRequest request) {
        String operation = blankToNull(request == null ? null : request.operation());
        ArtifactRetentionOperation parsed = enumValue(operation, ArtifactRetentionOperation.class, "operation");
        if (parsed == null) throw new IllegalArgumentException("Only PIN, RECYCLE, or RESTORE are supported for batch artifact retention.");
        var results = new java.util.ArrayList<BatchTaskRetentionResponse.TaskResult>();
        for (Long rawId : request.taskIds()) {
            if (rawId == null) continue;
            try { ensureOwned(TaskId.of(rawId), currentSubject.get()); retentionUseCase.change(TaskId.of(rawId), currentSubject.get(), parsed); results.add(new BatchTaskRetentionResponse.TaskResult(rawId, "CHANGED", null)); }
            catch (RuntimeException ex) { results.add(new BatchTaskRetentionResponse.TaskResult(rawId, "FAILED", ex.getMessage())); }
        }
        return ResponseEntity.ok(R.ok(new BatchTaskRetentionResponse(results)));
    }

    private BatchTaskRetentionResponse batchInput(BatchTaskCleanupRequest request) {
        if (request == null || request.taskIds().isEmpty()) throw new IllegalArgumentException("taskIds are required.");
        String operation = blankToNull(request.operation());
        if (operation == null) throw new IllegalArgumentException("operation is required.");
        var results = new java.util.ArrayList<BatchTaskRetentionResponse.TaskResult>();
        for (Long rawId : request.taskIds()) {
            if (rawId == null) continue;
            try {
                TaskId id = TaskId.of(rawId); ensureOwned(id, currentSubject.get());
                String workspace = workspaceFor(id, currentSubject.get());
                if ("PURGE".equalsIgnoreCase(operation)) {
                    if (inputPurgeUseCase == null) throw new IllegalStateException("Input purge service is not configured.");
                    String confirmation = request.confirmations().get(String.valueOf(rawId));
                    inputPurgeUseCase.request(id, currentSubject.get(), confirmation, workspace);
                } else if ("PIN".equalsIgnoreCase(operation)) {
                    inputRetentionUseCase.changePolicy(id, workspace, TaskInputRetention.PolicyChoice.PERMANENT, currentSubject.get());
                } else if ("SET_EXPIRY".equalsIgnoreCase(operation)) {
                    inputRetentionUseCase.schedule(id, workspace, parseInstant(request.expiresAt(), "expiresAt"), currentSubject.get());
                } else throw new IllegalArgumentException("operation is invalid.");
                results.add(new BatchTaskRetentionResponse.TaskResult(rawId, "CHANGED", null));
            } catch (RuntimeException ex) { results.add(new BatchTaskRetentionResponse.TaskResult(rawId, "FAILED", ex.getMessage())); }
        }
        return new BatchTaskRetentionResponse(results);
    }

    private void ensureOwned(TaskId taskId, AccessSubject subject) {
        if (taskRepository != null && taskRepository.findById(taskId).filter(task -> task.creator().equals(subject)).isEmpty())
            throw new java.util.NoSuchElementException("Processing task not found.");
    }

    private String workspaceFor(TaskId taskId, AccessSubject subject) {
        if (historyRepository == null) return "default";
        return historyRepository.findWorkspace(taskId, subject).orElseThrow(() ->
            new java.util.NoSuchElementException("Processing task not found."));
    }

    private void requireInputRetentionConfigured() {
        if (inputRetentionUseCase == null) throw new IllegalStateException("Input retention service is not configured.");
    }

    @PostMapping("/processing-tasks/{taskId}/artifact-retention")
    public ResponseEntity<R<TaskArtifactRetention>> changeRetention(
        @PathVariable long taskId, @RequestBody ArtifactRetentionRequest request
    ) {
        ArtifactRetentionOperation operation = enumValue(request == null ? null : request.operation(),
            ArtifactRetentionOperation.class, "operation");
        if (operation == null) {
            throw new IllegalArgumentException("operation is required.");
        }
        TaskArtifactRetention retention = retentionUseCase.change(TaskId.of(taskId), currentSubject.get(), operation);
        return ResponseEntity.ok(R.ok(retention));
    }

    @PostMapping("/processing-tasks/{taskId}/artifact-purge")
    public ResponseEntity<R<TaskArtifactRetention>> purge(
        @PathVariable long taskId, @RequestBody ArtifactPurgeRequest request
    ) {
        if (request == null || request.taskNo() == null || request.taskNo().isBlank()) {
            throw new IllegalArgumentException("Task number confirmation is required.");
        }
        TaskArtifactRetention retention = purgeUseCase.request(TaskId.of(taskId), currentSubject.get(), request.taskNo());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(R.ok(retention));
    }

    /** Compatibility method; the mapped endpoint now lives in ProcessingStorageController. */
    public ResponseEntity<R<ProcessingStorageSummaryResponse>> storageSummary() {
        var summary = historyQuery.storageSummary(currentSubject.get());
        return ResponseEntity.ok(R.ok(new ProcessingStorageSummaryResponse(summary.availableArtifactCount(),
            summary.recycleBinBytes(), summary.totalArtifactBytes())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant parseInstant(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        try {
            return Instant.parse(normalized);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 instant.", exception);
        }
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        try {
            return Enum.valueOf(type, normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " is invalid.", exception);
        }
    }
}
