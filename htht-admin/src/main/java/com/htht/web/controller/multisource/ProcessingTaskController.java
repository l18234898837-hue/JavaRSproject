package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.config.multisource.ServerOutputUriAllocator;
import com.htht.config.multisource.SysFileAssetStorageAdapter;
import com.htht.multisource.application.port.out.PreflightSessionStore;
import com.htht.multisource.application.port.out.model.PreflightSession;
import com.htht.multisource.application.usecase.CreateProcessingTaskCommand;
import com.htht.multisource.application.usecase.CreateProcessingTaskUseCase;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.PreflightProductUseCase;
import com.htht.multisource.application.usecase.model.PreflightResult;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.web.controller.multisource.dto.CreateTaskRequest;
import com.htht.web.controller.multisource.dto.PreflightRequest;
import com.htht.web.controller.multisource.dto.PreflightResponse;
import com.htht.web.controller.multisource.dto.TaskIdResponse;
import com.htht.web.controller.multisource.dto.TaskDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/multisource/processing-tasks")
public class ProcessingTaskController {
    private final Supplier<AccessSubject> currentSubject;
    private final SysFileAssetStorageAdapter assetAdapter;
    private final PreflightProductUseCase preflightUseCase;
    private final PreflightSessionStore sessionStore;
    private final CreateProcessingTaskUseCase createUseCase;
    private final ServerOutputUriAllocator outputUriAllocator;
    private final GetTaskDetailUseCase detailUseCase;

    public ProcessingTaskController(Supplier<AccessSubject> currentSubject, SysFileAssetStorageAdapter assetAdapter,
                                    PreflightProductUseCase preflightUseCase, PreflightSessionStore sessionStore,
                                    CreateProcessingTaskUseCase createUseCase, ServerOutputUriAllocator outputUriAllocator,
                                    GetTaskDetailUseCase detailUseCase) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.assetAdapter = Objects.requireNonNull(assetAdapter, "assetAdapter");
        this.preflightUseCase = Objects.requireNonNull(preflightUseCase, "preflightUseCase");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.createUseCase = Objects.requireNonNull(createUseCase, "createUseCase");
        this.outputUriAllocator = Objects.requireNonNull(outputUriAllocator, "outputUriAllocator");
        this.detailUseCase = Objects.requireNonNull(detailUseCase, "detailUseCase");
    }

    @PostMapping("/preflight")
    public ResponseEntity<R<PreflightResponse>> preflight(@RequestBody PreflightRequest request) {
        AccessSubject subject = currentSubject.get();
        List<InputAsset> assets = request.assets().stream().map(item -> assetAdapter.toInputAsset(
            item.role(), item.ordinal(), new AssetReference(item.sourceType(), Long.toString(item.sourceId())), item.required()
        )).toList();
        PreflightResult result = preflightUseCase.preflight(subject, assets);
        PreflightSession session = sessionStore.open(subject, assets, result.candidates());
        PreflightResponse response = new PreflightResponse(result.rulePackageVersion(), session.token(), session.expiresAt(),
            session.candidates().stream().map(item -> new PreflightResponse.CandidateResponse(
                item.id(), item.candidate().packageId(), item.candidate().supportStatus().name(), item.candidate().confidence(),
                new PreflightResponse.ProfileKeyResponse(
                    item.candidate().profileKey().productCode(), item.candidate().profileKey().profileVersion()
                )
            )).toList());
        return ResponseEntity.ok(R.ok(response));
    }

    @PostMapping
    public ResponseEntity<R<TaskIdResponse>> create(@RequestBody CreateTaskRequest request) {
        AccessSubject subject = currentSubject.get();
        PreflightSessionStore.SelectionLease lease = sessionStore.acquire(subject, request.preflightToken(), List.of(request.candidateId()));
        try {
            long taskId = createUseCase.create(new CreateProcessingTaskCommand(
                request.name(), subject, lease.candidates().get(0), request.parameterSnapshot(), outputUriAllocator.allocate(subject)
            )).value();
            lease.commit();
            return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(new TaskIdResponse(taskId)));
        } catch (RuntimeException exception) {
            lease.release();
            throw exception;
        }
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<R<TaskDetailResponse>> detail(@PathVariable long taskId) {
        var detail = detailUseCase.get(new com.htht.multisource.domain.model.TaskId(taskId), currentSubject.get());
        return ResponseEntity.ok(R.ok(new TaskDetailResponse(detail.id().value(), detail.batchId(),
            detail.lifecycleStatus().name(), detail.qualityStatus().name(), detail.plan().version(),
            detail.candidates().stream().map(candidate -> new TaskDetailResponse.CandidateResponse(
                candidate.packageId(), candidate.profileKey().productCode(), candidate.profileKey().profileVersion(),
                candidate.dataShape().name(), candidate.supportStatus().name(), candidate.confidence(),
                candidate.files().stream().map(asset -> new TaskDetailResponse.InputAssetResponse(
                    asset.role(), asset.ordinal(), asset.reference().sourceType(), asset.reference().sourceId(),
                    asset.storageUriSnapshot().value(), asset.checksum(), asset.required()
                )).toList()
            )).toList(),
            detail.stages().stream().map(stage -> new TaskDetailResponse.StageResponse(
                stage.id().value(), stage.stageKey(), stage.status().name(), stage.attempt()
            )).toList(),
            detail.artifacts().stream().map(artifact -> new TaskDetailResponse.ArtifactResponse(
                artifact.stageId().value(), artifact.type(), artifact.uri().value(), artifact.checksum()
            )).toList()
        )));
    }
}
