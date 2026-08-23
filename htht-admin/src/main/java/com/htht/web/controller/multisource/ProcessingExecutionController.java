package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.multisource.application.usecase.CancelProcessingTaskUseCase;
import com.htht.multisource.application.usecase.DispatchNextStageUseCase;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.RetryFailedStageUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.web.controller.multisource.dto.DispatchStageResponse;
import com.htht.web.controller.multisource.dto.ExecutionProgressResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/multisource")
public class ProcessingExecutionController {
    private final Supplier<AccessSubject> currentSubject;
    private final GetTaskDetailUseCase detailUseCase;
    private final DispatchNextStageUseCase dispatchUseCase;
    private final CancelProcessingTaskUseCase cancelUseCase;
    private final RetryFailedStageUseCase retryUseCase;

    public ProcessingExecutionController(Supplier<AccessSubject> currentSubject, GetTaskDetailUseCase detailUseCase,
                                         DispatchNextStageUseCase dispatchUseCase,
                                         CancelProcessingTaskUseCase cancelUseCase,
                                         RetryFailedStageUseCase retryUseCase) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.detailUseCase = Objects.requireNonNull(detailUseCase, "detailUseCase");
        this.dispatchUseCase = Objects.requireNonNull(dispatchUseCase, "dispatchUseCase");
        this.cancelUseCase = Objects.requireNonNull(cancelUseCase, "cancelUseCase");
        this.retryUseCase = Objects.requireNonNull(retryUseCase, "retryUseCase");
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
            detail.stages().stream().map(stage -> new ExecutionProgressResponse.StageProgressResponse(
                stage.id().value(), stage.stageKey(), stage.status().name(), stage.attempt()
            )).toList(),
            detail.artifacts().stream().map(artifact -> new ExecutionProgressResponse.ArtifactProgressResponse(
                artifact.stageId().value(), artifact.type(), artifact.uri().value(), artifact.checksum()
            )).toList()
        )));
    }
}
