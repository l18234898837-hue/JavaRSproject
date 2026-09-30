package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.multisource.application.port.out.TaskArtifactRetentionRepository;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.TaskArtifactPurgeUseCase;
import com.htht.multisource.application.usecase.TaskArtifactRetentionUseCase;
import com.htht.multisource.application.usecase.TaskHistoryQueryUseCase;
import com.htht.multisource.application.usecase.model.ArtifactRetentionOperation;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.web.controller.multisource.dto.ArtifactPurgeRequest;
import com.htht.web.controller.multisource.dto.ArtifactRetentionRequest;
import com.htht.web.controller.multisource.dto.ProcessingStorageSummaryResponse;
import com.htht.web.controller.multisource.dto.TaskHistoryDetailResponse;
import com.htht.web.controller.multisource.dto.TaskHistoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public TaskHistoryController(
        Supplier<AccessSubject> currentSubject,
        TaskHistoryQueryUseCase historyQuery,
        GetTaskDetailUseCase detailUseCase,
        TaskArtifactRetentionRepository retentionRepository,
        TaskArtifactRetentionUseCase retentionUseCase,
        TaskArtifactPurgeUseCase purgeUseCase
    ) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.historyQuery = Objects.requireNonNull(historyQuery, "historyQuery");
        this.detailUseCase = Objects.requireNonNull(detailUseCase, "detailUseCase");
        this.retentionRepository = Objects.requireNonNull(retentionRepository, "retentionRepository");
        this.retentionUseCase = Objects.requireNonNull(retentionUseCase, "retentionUseCase");
        this.purgeUseCase = Objects.requireNonNull(purgeUseCase, "purgeUseCase");
    }

    @GetMapping("/processing-tasks/history")
    public ResponseEntity<R<TaskHistoryResponse>> history(
        @RequestParam(required = false) String productCode,
        @RequestParam(required = false) String lifecycle,
        @RequestParam(required = false) String completedFrom,
        @RequestParam(required = false) String completedTo,
        @RequestParam(required = false) String retention,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        AccessSubject subject = currentSubject.get();
        var result = historyQuery.query(new TaskHistoryQuery(subject, blankToNull(productCode),
            enumValue(lifecycle, TaskLifecycleStatus.class, "lifecycle"), parseInstant(completedFrom, "completedFrom"),
            parseInstant(completedTo, "completedTo"), enumValue(retention, TaskArtifactRetentionStatus.class, "retention"), page, pageSize));
        return ResponseEntity.ok(R.ok(new TaskHistoryResponse(result.items(), result.total(), result.page(), result.pageSize())));
    }

    @GetMapping("/processing-tasks/{taskId}/history-detail")
    public ResponseEntity<R<TaskHistoryDetailResponse>> detail(@PathVariable long taskId) {
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        TaskArtifactRetention retention = retentionRepository.findOrCreate(detail.id());
        long totalBytes = detail.artifacts().stream().mapToLong(artifact -> Math.max(0L, artifact.sizeBytes())).sum();
        return ResponseEntity.ok(R.ok(new TaskHistoryDetailResponse(detail, retention, totalBytes)));
    }

    @PostMapping("/processing-tasks/{taskId}/artifact-retention")
    public ResponseEntity<R<TaskArtifactRetention>> changeRetention(
        @PathVariable long taskId, @RequestBody ArtifactRetentionRequest request
    ) {
        ArtifactRetentionOperation operation = enumValue(request == null ? null : request.operation(),
            ArtifactRetentionOperation.class, "operation");
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

    @GetMapping("/processing-storage/summary")
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
