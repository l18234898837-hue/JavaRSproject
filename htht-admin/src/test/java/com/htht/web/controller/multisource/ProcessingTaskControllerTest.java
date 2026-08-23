package com.htht.web.controller.multisource;

import com.htht.config.multisource.ServerOutputUriAllocator;
import com.htht.config.multisource.SysFileAssetStorageAdapter;
import com.htht.multisource.application.port.out.PreflightSessionStore;
import com.htht.multisource.application.port.out.model.PreflightSession;
import com.htht.multisource.application.usecase.CreateProcessingTaskUseCase;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.PreflightProductUseCase;
import com.htht.multisource.application.usecase.model.PreflightResult;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import com.htht.web.controller.multisource.dto.AssetRequest;
import com.htht.web.controller.multisource.dto.CreateTaskRequest;
import com.htht.web.controller.multisource.dto.PreflightRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
public class ProcessingTaskControllerTest {

    @TempDir
    Path uploadRoot;

    @Test
    void preflightPersistsServerCandidatesAndCreateUsesOnlyTheTokenSelection() throws Exception {
        Path path = Files.writeString(uploadRoot.resolve("product.hdf"), "ocean");
        ISysFileService fileService = mock(ISysFileService.class);
        SysFile sysFile = new SysFile();
        sysFile.setId(42L);
        sysFile.setFilePath(path.toString());
        when(fileService.getById(42L)).thenReturn(sysFile);
        SysFileAssetStorageAdapter assets = new SysFileAssetStorageAdapter(fileService, uploadRoot);
        ProductCandidate candidate = candidate();
        PreflightProductUseCase preflight = mock(PreflightProductUseCase.class);
        when(preflight.preflight(any(), any())).thenReturn(new PreflightResult("rules-1", List.of(candidate)));
        PreflightSessionStore sessions = mock(PreflightSessionStore.class);
        when(sessions.open(any(), any(), any())).thenReturn(new PreflightSession(
            "token-1", Instant.parse("2026-08-20T12:30:00Z"), List.of(asset()),
            List.of(new PreflightSession.Candidate("c0", candidate))
        ));
        PreflightSessionStore.SelectionLease lease = mock(PreflightSessionStore.SelectionLease.class);
        when(lease.candidates()).thenReturn(List.of(candidate));
        when(sessions.acquire(any(), any(), any())).thenReturn(lease);
        CreateProcessingTaskUseCase create = mock(CreateProcessingTaskUseCase.class);
        when(create.create(any())).thenReturn(TaskId.of(101L));
        ProcessingTaskController controller = new ProcessingTaskController(
            () -> new AccessSubject(7L), assets, preflight, sessions, create,
            new ServerOutputUriAllocator(() -> "request-1"), mock(GetTaskDetailUseCase.class)
        );

        var preflightResponse = controller.preflight(new PreflightRequest(List.of(new AssetRequest("SYS_FILE", 42L, "MAIN", 0, true))));
        var createResponse = controller.create(new CreateTaskRequest("task", "token-1", "c0", "{}"));

        assertThat(preflightResponse.getStatusCodeValue()).isEqualTo(200);
        assertThat(preflightResponse.getBody().getData().preflightToken()).isEqualTo("token-1");
        assertThat(createResponse.getStatusCodeValue()).isEqualTo(201);
        assertThat(createResponse.getBody().getData().taskId()).isEqualTo(101L);
        verify(lease).commit();
    }

    @Test
    void delegatesTaskDetailLookupWithTheAuthenticatedSubject() {
        GetTaskDetailUseCase detail = mock(GetTaskDetailUseCase.class);
        when(detail.get(TaskId.of(101L), new AccessSubject(7L))).thenReturn(new TaskDetailView(
            TaskId.of(101L), 33L, TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(candidate()),
            List.of(new com.htht.multisource.application.usecase.model.TaskStageView(
                new com.htht.multisource.domain.model.StageId(201L), "READ", StageExecutionStatus.PENDING, 1
            )),
            List.of(new com.htht.multisource.application.usecase.model.TaskArtifactView(
                new com.htht.multisource.domain.model.StageId(201L), "SOURCE", new StorageUri("rs://artifact/201"), "sha256:artifact"
            ))
        ));
        ProcessingTaskController controller = new ProcessingTaskController(
            () -> new AccessSubject(7L), new SysFileAssetStorageAdapter(mock(ISysFileService.class), uploadRoot), mock(PreflightProductUseCase.class),
            mock(PreflightSessionStore.class), mock(CreateProcessingTaskUseCase.class),
            new ServerOutputUriAllocator(() -> "request-1"), detail
        );

        var response = controller.detail(101L);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().taskId()).isEqualTo(101L);
        assertThat(response.getBody().getData().lifecycleStatus()).isEqualTo("CREATED");
        assertThat(response.getBody().getData().planVersion()).isEqualTo("plan-1");
        assertThat(response.getBody().getData().candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.packageId()).isEqualTo("pkg");
            assertThat(candidate.inputAssets()).singleElement().satisfies(asset ->
                assertThat(asset.storageUri()).isEqualTo("rs://sys-file/42"));
        });
        assertThat(response.getBody().getData().stages()).singleElement().satisfies(stage -> {
            assertThat(stage.stageId()).isEqualTo(201L);
            assertThat(stage.status()).isEqualTo("PENDING");
        });
        assertThat(response.getBody().getData().artifacts()).singleElement().satisfies(artifact ->
            assertThat(artifact.uri()).isEqualTo("rs://artifact/201"));
        verify(detail).get(TaskId.of(101L), new AccessSubject(7L));
    }

    private InputAsset asset() {
        return new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), new StorageUri("rs://sys-file/42"), "sha256:test", true);
    }

    private ProductCandidate candidate() {
        return new ProductCandidate("pkg", new ProductProfileKey("FY-3H-MERSI", "v1"), ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.SUPPORTED, List.of(asset()), 0.9);
    }
}
