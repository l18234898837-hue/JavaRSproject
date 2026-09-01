package com.htht.web.controller.multisource;

import cn.dev33.satoken.annotation.SaIgnore;
import com.htht.common.core.domain.R;
import com.htht.config.multisource.LocalWorkerResultArchiveService;
import com.htht.config.multisource.ResultDownloadTicketService;
import com.htht.multisource.application.usecase.CancelProcessingTaskUseCase;
import com.htht.multisource.application.usecase.DispatchNextStageUseCase;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.RetryFailedStageUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.worker.WorkerFailurePresentation;
import com.htht.web.controller.multisource.dto.DispatchStageResponse;
import com.htht.web.controller.multisource.dto.ExecutionProgressResponse;
import com.htht.web.controller.multisource.dto.ResultDownloadTicketRequest;
import com.htht.web.controller.multisource.dto.ResultDownloadTicketResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/multisource")
public class ProcessingExecutionController {
    private static final Logger log = LoggerFactory.getLogger(ProcessingExecutionController.class);
    private final Supplier<AccessSubject> currentSubject;
    private final GetTaskDetailUseCase detailUseCase;
    private final DispatchNextStageUseCase dispatchUseCase;
    private final CancelProcessingTaskUseCase cancelUseCase;
    private final RetryFailedStageUseCase retryUseCase;
    private final LocalWorkerResultArchiveService resultArchiveService;
    private final ResultDownloadTicketService downloadTicketService;

    @Autowired
    public ProcessingExecutionController(Supplier<AccessSubject> currentSubject, GetTaskDetailUseCase detailUseCase,
                                         DispatchNextStageUseCase dispatchUseCase,
                                         CancelProcessingTaskUseCase cancelUseCase,
                                         RetryFailedStageUseCase retryUseCase,
                                         LocalWorkerResultArchiveService resultArchiveService,
                                         ResultDownloadTicketService downloadTicketService) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.detailUseCase = Objects.requireNonNull(detailUseCase, "detailUseCase");
        this.dispatchUseCase = Objects.requireNonNull(dispatchUseCase, "dispatchUseCase");
        this.cancelUseCase = Objects.requireNonNull(cancelUseCase, "cancelUseCase");
        this.retryUseCase = Objects.requireNonNull(retryUseCase, "retryUseCase");
        this.resultArchiveService = Objects.requireNonNull(resultArchiveService, "resultArchiveService");
        this.downloadTicketService = Objects.requireNonNull(downloadTicketService, "downloadTicketService");
    }

    public ProcessingExecutionController(Supplier<AccessSubject> currentSubject, GetTaskDetailUseCase detailUseCase,
                                         DispatchNextStageUseCase dispatchUseCase,
                                         CancelProcessingTaskUseCase cancelUseCase,
                                         RetryFailedStageUseCase retryUseCase) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.detailUseCase = Objects.requireNonNull(detailUseCase, "detailUseCase");
        this.dispatchUseCase = Objects.requireNonNull(dispatchUseCase, "dispatchUseCase");
        this.cancelUseCase = Objects.requireNonNull(cancelUseCase, "cancelUseCase");
        this.retryUseCase = Objects.requireNonNull(retryUseCase, "retryUseCase");
        this.resultArchiveService = null;
        this.downloadTicketService = null;
    }

    @PostMapping("/processing-tasks/{taskId}/dispatch")
    public ResponseEntity<R<DispatchStageResponse>> dispatch(@PathVariable long taskId) {
        TaskId id = TaskId.of(taskId);
        detailUseCase.get(id, currentSubject.get());
        var result = dispatchUseCase.dispatch(id);
        DispatchStageResponse response = result
            .map(value -> new DispatchStageResponse(true, value.stageId()))
            .orElseGet(() -> new DispatchStageResponse(false, null));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(R.ok(response));
    }

    @PostMapping("/processing-tasks/{taskId}/cancel")
    public ResponseEntity<R<Void>> cancel(@PathVariable long taskId) {
        cancelUseCase.cancel(TaskId.of(taskId), currentSubject.get());
        return ResponseEntity.ok(R.ok());
    }

    @PostMapping("/processing-stages/{stageId}/retry")
    public ResponseEntity<R<Void>> retry(@PathVariable long stageId) {
        retryUseCase.retry(new StageId(stageId), currentSubject.get());
        return ResponseEntity.ok(R.ok());
    }

    @GetMapping("/processing-tasks/{taskId}/progress")
    public ResponseEntity<R<ExecutionProgressResponse>> progress(@PathVariable long taskId) {
        var detail = detailUseCase.get(TaskId.of(taskId), currentSubject.get());
        return ResponseEntity.ok(R.ok(new ExecutionProgressResponse(
            detail.id().value(), detail.lifecycleStatus().name(), detail.qualityStatus().name(),
            detail.stages().stream().map(stage -> {
                WorkerFailurePresentation.SafeFailure safeFailure = WorkerFailurePresentation.forClient(
                    stage.errorCode(), stage.errorMessage()
                );
                return new ExecutionProgressResponse.StageProgressResponse(
                    stage.id().value(), stage.stageKey(), stage.status().name(), stage.attempt(),
                    stage.errorCode() == null && stage.errorMessage() == null ? null : safeFailure.code(),
                    stage.errorCode() == null && stage.errorMessage() == null ? null : safeFailure.message()
                );
            }).toList(),
            detail.artifacts().stream().map(artifact -> new ExecutionProgressResponse.ArtifactProgressResponse(
                artifact.stageId().value(), artifact.type(), artifact.uri().value(), artifact.checksum()
            )).toList()
        )));
    }

    @PostMapping("/processing-tasks/{taskId}/result-download-ticket")
    public ResponseEntity<R<ResultDownloadTicketResponse>> createResultDownloadTicket(
        @PathVariable long taskId, @RequestBody ResultDownloadTicketRequest request) {
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        if (!"SUCCEEDED".equals(detail.lifecycleStatus().name())) {
            throw new IllegalStateException("Processing task has not completed successfully.");
        }
        if (downloadTicketService == null) {
            throw new IllegalStateException("Result download ticket service is not configured.");
        }
        var issued = downloadTicketService.issue(taskId, subject, request.fileName());
        return ResponseEntity.ok(R.ok(new ResultDownloadTicketResponse(issued.token(), issued.expiresAt())));
    }

    @SaIgnore
    @GetMapping("/processing-tasks/{taskId}/result.zip")
    public void download(@PathVariable long taskId, @RequestParam("ticket") String ticket,
                         javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (downloadTicketService == null) {
            throw new IllegalStateException("Result download ticket service is not configured.");
        }
        var grant = downloadTicketService.consume(taskId, ticket);
        AccessSubject subject = grant.subject();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        if (!"SUCCEEDED".equals(detail.lifecycleStatus().name())) {
            throw new IllegalStateException("Processing task has not completed successfully.");
        }
        if (resultArchiveService == null) {
            throw new IllegalStateException("Result archive service is not configured.");
        }
        try {
            resultArchiveService.write(detail, subject, grant.fileName(), response);
        } catch (java.io.IOException exception) {
            if (response.isCommitted()) {
                // Once ZIP headers or bytes have been sent, a JSON error response is no
                // longer valid. Client cancellation is the common cause for this path.
                log.warn("Result ZIP stream ended early for task {}: {}", taskId, exception.getMessage());
                return;
            }
            throw exception;
        }
    }
}
