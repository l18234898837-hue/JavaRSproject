package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.config.multisource.ServerOutputUriAllocator;
import com.htht.multisource.application.port.out.PreflightSessionStore;
import com.htht.multisource.application.usecase.CreateProcessingBatchUseCase;
import com.htht.multisource.application.usecase.model.BatchCreateCommand;
import com.htht.web.controller.multisource.dto.BatchCreationResponse;
import com.htht.web.controller.multisource.dto.CreateBatchRequest;
import com.htht.multisource.domain.model.AccessSubject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/multisource/processing-batches")
public class ProcessingBatchController {
    private final Supplier<AccessSubject> currentSubject;
    private final PreflightSessionStore sessionStore;
    private final CreateProcessingBatchUseCase createUseCase;
    private final ServerOutputUriAllocator outputUriAllocator;

    public ProcessingBatchController(Supplier<AccessSubject> currentSubject, PreflightSessionStore sessionStore,
                                     CreateProcessingBatchUseCase createUseCase, ServerOutputUriAllocator outputUriAllocator) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.createUseCase = Objects.requireNonNull(createUseCase, "createUseCase");
        this.outputUriAllocator = Objects.requireNonNull(outputUriAllocator, "outputUriAllocator");
    }

    @PostMapping
    public ResponseEntity<R<BatchCreationResponse>> create(@RequestBody CreateBatchRequest request) {
        AccessSubject subject = currentSubject.get();
        PreflightSessionStore.SelectionLease lease = sessionStore.acquire(subject, request.preflightToken(), request.candidateIds());
        try {
            var result = createUseCase.create(new BatchCreateCommand(
                request.name(), subject, lease.candidates(), request.parameterSnapshot(), outputUriAllocator.allocate(subject)
            ));
            lease.commit();
            BatchCreationResponse response = new BatchCreationResponse(result.batch().id(),
                result.taskIds().stream().map(taskId -> taskId.value()).toList());
            return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(response));
        } catch (RuntimeException exception) {
            lease.release();
            throw exception;
        }
    }
}
