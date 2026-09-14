package com.htht.web.controller.multisource;

import com.htht.config.multisource.LocalWorkerResultArchiveService;
import com.htht.config.multisource.MultiSourceWorkerProperties;
import com.htht.config.multisource.ResultDownloadTicketService;
import com.htht.config.multisource.ArtifactPreviewTicketService;
import com.htht.config.multisource.ArtifactRasterPreviewService;
import com.htht.multisource.application.usecase.CancelProcessingTaskUseCase;
import com.htht.multisource.application.usecase.DispatchNextStageUseCase;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.RetryFailedStageUseCase;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskStageView;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.StorageUri;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class ProcessingExecutionControllerTest {
    private static final AccessSubject OWNER = new AccessSubject(7L);
    private static final TaskId TASK_ID = TaskId.of(101L);
    private static final StageId STAGE_ID = new StageId(201L);

    @TempDir
    Path temporaryDirectory;

    @Test
    void dispatchAuthorizesOwnerBeforeDispatchAndDoesNotExposeExecutionId() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(detail());
        DispatchNextStageUseCase dispatch = mock(DispatchNextStageUseCase.class);
        when(dispatch.dispatch(TASK_ID)).thenReturn(Optional.of(new DispatchNextStageUseCase.DispatchResult(201L, "execution-secret")));
        ProcessingExecutionController controller = controller(details, dispatch, mock(CancelProcessingTaskUseCase.class),
            mock(RetryFailedStageUseCase.class));

        var response = controller.dispatch(101L);

        assertThat(response.getStatusCodeValue()).isEqualTo(202);
        assertThat(response.getBody().getData().dispatched()).isTrue();
        assertThat(response.getBody().getData().stageId()).isEqualTo(201L);
        verify(details).get(TASK_ID, OWNER);
        verify(dispatch).dispatch(TASK_ID);
    }

    @Test
    void cancelAndRetryDelegateWithTheAuthenticatedOwner() {
        CancelProcessingTaskUseCase cancel = mock(CancelProcessingTaskUseCase.class);
        RetryFailedStageUseCase retry = mock(RetryFailedStageUseCase.class);
        ProcessingExecutionController controller = controller(mock(GetTaskDetailUseCase.class), mock(DispatchNextStageUseCase.class),
            cancel, retry);

        var cancelResponse = controller.cancel(101L);
        var retryResponse = controller.retry(201L);

        assertThat(cancelResponse.getStatusCodeValue()).isEqualTo(200);
        assertThat(retryResponse.getStatusCodeValue()).isEqualTo(200);
        verify(cancel).cancel(TASK_ID, OWNER);
        verify(retry).retry(STAGE_ID, OWNER);
    }

    @Test
    void progressUsesOwnerCheckedTaskDetailAndReturnsOnlyProgressFields() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(detail());
        ProcessingExecutionController controller = controller(details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class));

        var response = controller.progress(101L);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().taskId()).isEqualTo(101L);
        assertThat(response.getBody().getData().lifecycleStatus()).isEqualTo("RUNNING");
        assertThat(response.getBody().getData().stages()).singleElement().satisfies(stage -> {
            assertThat(stage.stageId()).isEqualTo(201L);
            assertThat(stage.status()).isEqualTo("RUNNING");
            assertThat(stage.attempt()).isEqualTo(1);
        });
        verify(details).get(TASK_ID, OWNER);
    }

    @Test
    void progressIncludesQualityReportSummaryWithoutExposingTheFullReport() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(succeededDetailWithDeclaredArtifact());
        ProcessingExecutionController controller = controller(details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class));

        var response = controller.progress(101L);

        assertThat(response.getBody().getData().qualityReportSummary()).satisfies(summary -> {
            assertThat(summary.overallStatus()).isEqualTo("NOT_CHECKED");
            assertThat(summary.totalChecks()).isGreaterThanOrEqualTo(2);
            assertThat(summary.failedChecks()).isZero();
        });
        verify(details).get(TASK_ID, OWNER);
    }

    @Test
    void qualityReportRequiresTerminalTaskAndReusesOwnerAuthorization() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(succeededDetailWithDeclaredArtifact());
        ProcessingExecutionController controller = controller(details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class));

        var response = controller.qualityReport(101L);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().taskId()).isEqualTo(101L);
        assertThat(response.getBody().getData().overallStatus()).isEqualTo("NOT_CHECKED");
        assertThat(response.getBody().getData().checks()).isNotEmpty();
        verify(details).get(TASK_ID, OWNER);
    }

    @Test
    void qualityReportRejectsNonTerminalTasks() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(detail());
        ProcessingExecutionController controller = controller(details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class));

        assertThatThrownBy(() -> controller.qualityReport(101L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("terminal state");
        verify(details).get(TASK_ID, OWNER);
    }

    @Test
    void qualityReportHtmlDownloadsEscapedReport() throws Exception {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(succeededDetailWithDeclaredArtifact());
        ProcessingExecutionController controller = controller(details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/multisource/processing-tasks/101/quality-report.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"task-101-quality-report.html\""))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("质量检测报告")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("总体结论：NOT_CHECKED")));
    }

    @Test
    void tilePreviewIssuesOnlyAnOpaqueRelativeTileTemplate() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        var artifact = artifact("STANDARD_COG", "standard.tif");
        var detail = new TaskDetailView(
            TASK_ID, 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.SUCCEEDED, 1)), List.of(artifact)
        );
        when(details.get(TASK_ID, OWNER)).thenReturn(detail);
        ArtifactRasterPreviewService rasterPreview = mock(ArtifactRasterPreviewService.class);
        when(rasterPreview.describe(any(), any(), any(), org.mockito.ArgumentMatchers.eq("COG"))).thenReturn(
            new ArtifactRasterPreviewService.RasterPreview(true, null, "EPSG:4326", List.of(110d, 30d, 111d, 31d),
                0, 16, java.util.Map.of("kind", "rgb", "bands", List.of(1, 2, 3)), null)
        );
        ProcessingExecutionController controller = controllerWithRasterPreview(details, rasterPreview);

        var response = controller.tilePreview(101L, "STANDARD_COG");

        assertThat(response.getBody().getData().tileUrl()).contains("{z}/{x}/{y}.png?ticket=");
        assertThat(response.getBody().getData().tileUrl()).doesNotContain("C:\\", "storageKey", "rs://");
        assertThat(response.getBody().getData().bounds()).containsExactly(110d, 30d, 111d, 31d);
        assertThat(response.getBody().getData().crs()).isEqualTo("EPSG:4326");
    }

    @Test
    void progressDescribesGeoreferencedPngPreviewUsingItsCompanionCog() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        var png = artifact("FUSION_PREVIEW_PNG", "fusion/czi-rgb.png");
        var cog = artifact("FUSION_COG", "fusion/czi-rgb.tif");
        var detail = new TaskDetailView(
            TASK_ID, 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.SUCCEEDED, 1)), List.of(png, cog)
        );
        when(details.get(TASK_ID, OWNER)).thenReturn(detail);
        ArtifactRasterPreviewService rasterPreview = mock(ArtifactRasterPreviewService.class);
        when(rasterPreview.describe(any(), any(), any(), org.mockito.ArgumentMatchers.eq("COG"))).thenReturn(
            new ArtifactRasterPreviewService.RasterPreview(true, null, "EPSG:32650", List.of(111d, 12d, 121d, 18d),
                0, 16, java.util.Map.of("kind", "rgb"), null)
        );
        ProcessingExecutionController controller = controllerWithRasterPreview(details, rasterPreview);

        var artifact = controller.progress(101L).getBody().getData().artifacts().stream()
            .filter(item -> item.type().equals("FUSION_PREVIEW_PNG")).findFirst().orElseThrow();

        assertThat(artifact.visualization()).isNotNull();
        assertThat(artifact.visualization().kind()).isEqualTo("tiles");
        assertThat(artifact.visualization().tileUrl())
            .startsWith("/api/multisource/processing-tasks/101/artifacts/FUSION_COG/tiles/{z}/{x}/{y}.png?ticket=");
        assertThat(artifact.visualization().imageUrl()).isNull();
        assertThat(artifact.visualization().bounds()).containsExactly(111d, 12d, 121d, 18d);
        assertThat(artifact.visualization().crs()).isNull();
    }

    @Test
    void progressUsesScoreCogForSaliencyPreviewPng() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        var png = artifact("SALIENCY_PREVIEW_PNG", "saliency/preview.png");
        var score = artifact("SALIENCY_SCORE_COG", "saliency/score.tif");
        var cog = artifact("SALIENCY_MASK_COG", "saliency/mask.tif");
        when(details.get(TASK_ID, OWNER)).thenReturn(new TaskDetailView(
            TASK_ID, 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.SUCCEEDED, 1)), List.of(png, cog, score)
        ));
        ArtifactRasterPreviewService rasterPreview = mock(ArtifactRasterPreviewService.class);
        when(rasterPreview.describe(any(), any(), any(), org.mockito.ArgumentMatchers.eq("COG"))).thenReturn(
            new ArtifactRasterPreviewService.RasterPreview(true, null, "EPSG:4326", List.of(110d, 20d, 120d, 30d),
                0, 16, java.util.Map.of("kind", "score"), null)
        );
        var artifact = controllerWithRasterPreview(details, rasterPreview).progress(101L).getBody().getData().artifacts()
            .stream().filter(item -> item.type().equals("SALIENCY_PREVIEW_PNG")).findFirst().orElseThrow();
        assertThat(artifact.visualization()).isNotNull();
        assertThat(artifact.visualization().bounds()).containsExactly(110d, 20d, 120d, 30d);
        assertThat(artifact.visualization().tileUrl())
            .contains("/artifacts/SALIENCY_SCORE_COG/tiles/{z}/{x}/{y}.png?ticket=");
    }

    @Test
    void tileRequiresItsMatchingTicketAndReturnsPngOnly() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        var artifact = artifact("STANDARD_COG", "standard.tif");
        var detail = new TaskDetailView(
            TASK_ID, 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.SUCCEEDED, 1)), List.of(artifact)
        );
        when(details.get(TASK_ID, OWNER)).thenReturn(detail);
        ArtifactRasterPreviewService rasterPreview = mock(ArtifactRasterPreviewService.class);
        var preview = new ArtifactRasterPreviewService.RasterPreview(true, null, "EPSG:4326", List.of(110d, 30d, 111d, 31d),
            0, 16, java.util.Map.of("kind", "rgb"), null);
        when(rasterPreview.describe(any(), any(), any(), org.mockito.ArgumentMatchers.eq("COG"))).thenReturn(preview);
        when(rasterPreview.renderTile(any(), any(), any(), org.mockito.ArgumentMatchers.eq("COG"),
            org.mockito.ArgumentMatchers.eq(preview), org.mockito.ArgumentMatchers.eq(4),
            org.mockito.ArgumentMatchers.eq(13), org.mockito.ArgumentMatchers.eq(7))).thenReturn(new byte[] {1, 2, 3});
        var tickets = new ArtifactPreviewTicketService();
        ProcessingExecutionController controller = controllerWithRasterPreview(details, rasterPreview, tickets);
        String ticket = tickets.issue(101L, OWNER, "STANDARD_COG", artifact.checksum()).token();

        var response = controller.tile(101L, "STANDARD_COG", 4, 13, 7, ticket);

        assertThat(response.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, max-age=60");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> controller.tile(101L, "STANDARD_COG", 4, 16, 7, ticket))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.tile(101L, "STANDARD_COG", 4, 13, 7, "other"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void imagePreviewUsesArtifactTicketAndServesInlinePng() throws Exception {
        Path workRoot = Files.createDirectories(temporaryDirectory.resolve("image-worker"));
        Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("image-artifacts"));
        Path image = Files.createDirectories(artifactRoot.resolve("101/work/READ/1/fusion"))
            .resolve("czi-rgb.png");
        Files.write(image, new byte[] {1, 2, 3});
        var png = new TaskArtifactView(STAGE_ID, "FUSION_PREVIEW_PNG",
            new StorageUri("rs://output/7/task-01/fusion/czi-rgb.png"), checksum(image), image.toFile().length(), "INTERMEDIATE",
            "101/work/READ/1/fusion/czi-rgb.png");
        var detail = new TaskDetailView(
            TASK_ID, 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.SUCCEEDED, 1)), List.of(png)
        );
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(detail);
        var tickets = new ArtifactPreviewTicketService();
        ProcessingExecutionController controller = new ProcessingExecutionController(
            () -> OWNER, details, mock(DispatchNextStageUseCase.class), mock(CancelProcessingTaskUseCase.class),
            mock(RetryFailedStageUseCase.class), new LocalWorkerResultArchiveService(properties(workRoot, artifactRoot)),
            new ResultDownloadTicketService(), null, tickets, mock(ArtifactRasterPreviewService.class));
        String ticket = tickets.issue(101L, OWNER, png.type(), png.checksum()).token();

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/multisource/processing-tasks/101/artifacts/FUSION_PREVIEW_PNG/image")
                .param("ticket", ticket))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"czi-rgb.png\""))
            .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void progressMarksOnlySupportedImageAndTextArtifactsAsPreviewable() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(new TaskDetailView(
            TASK_ID, 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.SUCCEEDED, 1)),
            List.of(
                artifact("PROCESSING_SUMMARY_JSON", "summary.json"),
                artifact("MAP_PREVIEW_PNG", "preview.png"),
                artifact("TRACK_GEOJSON", "track.geojson"),
                artifact("STANDARD_COG", "standard.tif"),
                artifact("STANDARD_TRACK_NETCDF", "track.nc"),
                artifact("STANDARD_TRACK_PARQUET", "track.parquet"),
                artifact("DECODED_CZI_RASTER", "decoded/czi.npz")
            )
        ));
        ProcessingExecutionController controller = controller(details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class));

        var artifacts = controller.progress(101L).getBody().getData().artifacts();

        assertThat(artifacts).extracting(artifact -> artifact.type(), artifact -> artifact.previewable())
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("PROCESSING_SUMMARY_JSON", true),
                org.assertj.core.groups.Tuple.tuple("MAP_PREVIEW_PNG", true),
                org.assertj.core.groups.Tuple.tuple("TRACK_GEOJSON", true),
                org.assertj.core.groups.Tuple.tuple("STANDARD_COG", false),
                org.assertj.core.groups.Tuple.tuple("STANDARD_TRACK_NETCDF", false),
                org.assertj.core.groups.Tuple.tuple("STANDARD_TRACK_PARQUET", false),
                org.assertj.core.groups.Tuple.tuple("DECODED_CZI_RASTER", false)
            );
        assertThat(artifacts).filteredOn(artifact -> artifact.type().equals("TRACK_GEOJSON"))
            .singleElement().satisfies(artifact -> assertThat(artifact.mediaType()).isEqualTo("application/geo+json"));
        assertThat(artifacts).filteredOn(artifact -> artifact.type().equals("STANDARD_COG"))
            .singleElement().satisfies(artifact -> {
                assertThat(artifact.format()).isEqualTo("COG");
                assertThat(artifact.view().enabled()).isTrue();
                assertThat(artifact.view().mode()).isEqualTo("map");
            });
        assertThat(artifacts).filteredOn(artifact -> artifact.type().equals("TRACK_GEOJSON"))
            .singleElement().satisfies(artifact -> assertThat(artifact.view().mode()).isEqualTo("map"));
        assertThat(artifacts).filteredOn(artifact -> artifact.type().equals("STANDARD_TRACK_NETCDF"))
            .singleElement().satisfies(artifact -> assertThat(artifact.view().mode()).isEqualTo("dataset"));
        assertThat(artifacts).filteredOn(artifact -> artifact.type().equals("STANDARD_TRACK_PARQUET"))
            .singleElement().satisfies(artifact -> assertThat(artifact.view().mode()).isEqualTo("dataset"));
        assertThat(artifacts).filteredOn(artifact -> artifact.type().equals("DECODED_CZI_RASTER"))
            .singleElement().satisfies(artifact -> {
                assertThat(artifact.format()).isEqualTo("NPZ");
                assertThat(artifact.view().enabled()).isFalse();
                assertThat(artifact.view().reason()).isEqualTo("NPZ 二进制中间数据暂不支持在线查看");
            });
    }

    @Test
    void progressReturnsActionableStageFailureDetails() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(new TaskDetailView(
            TASK_ID, 33L, TaskLifecycleStatus.FAILED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.FAILED, 1,
                "INVALID_REQUEST", "L2A ROI bounds are invalid")), List.of()
        ));
        ProcessingExecutionController controller = controller(details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class));

        var response = controller.progress(101L);

        assertThat(response.getBody().getData().stages()).singleElement().satisfies(stage -> {
            assertThat(stage.errorCode()).isEqualTo("INVALID_REQUEST");
            assertThat(stage.errorMessage()).isEqualTo("L2A ROI bounds are invalid");
        });
    }

    @Test
    void progressFailsClosedForHistoricalUnsafeWorkerErrorDetails() {
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(new TaskDetailView(
            TASK_ID, 33L, TaskLifecycleStatus.FAILED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.FAILED, 1,
                "C:\\secret\\worker-error", "failed to read C:\\secret\\work\\input.tif\nstack detail")), List.of()
        ));
        ProcessingExecutionController controller = controller(details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class));

        var response = controller.progress(101L);

        assertThat(response.getBody().getData().stages()).singleElement().satisfies(stage -> {
            assertThat(stage.errorCode()).isEqualTo("WORKER_DISPATCH_FAILED");
            assertThat(stage.errorMessage()).isEqualTo("Worker 执行失败，请稍后重试");
            assertThat(stage.errorCode()).doesNotContain("C:\\secret");
            assertThat(stage.errorMessage()).doesNotContain("C:\\secret");
            assertThat(stage.errorMessage()).doesNotContain("stack detail");
        });
    }

    @Test
    void downloadsALegacyArtifactFromItsCompletedWorkerExecution() throws Exception {
        Path workRoot = Files.createDirectories(temporaryDirectory.resolve("worker"));
        Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
        Path source = Files.writeString(
            Files.createDirectories(workRoot.resolve("exec-01/output/validation")).resolve("source.json"),
            "{\"valid\":true}"
        );
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(detailWithArtifact(source, "exec-01"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controllerWithDownloads(details, workRoot, artifactRoot)).build();

        mvc.perform(get("/api/multisource/processing-tasks/101/artifacts/HY1C_INPUT_VALIDATION_JSON/download"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"source.json\""))
            .andExpect(content().string("{\"valid\":true}"));
    }

    @Test
    void downloadsAMaterializedArtifactAfterTheWorkerExecutionIsUnavailable() throws Exception {
        Path workRoot = Files.createDirectories(temporaryDirectory.resolve("worker"));
        Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
        Path source = Files.writeString(
            Files.createDirectories(artifactRoot.resolve("101/work/READ/1/validation")).resolve("source.json"),
            "{\"valid\":true}"
        );
        GetTaskDetailUseCase details = mock(GetTaskDetailUseCase.class);
        when(details.get(TASK_ID, OWNER)).thenReturn(detailWithArtifact(source, null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controllerWithDownloads(details, workRoot, artifactRoot)).build();

        mvc.perform(get("/api/multisource/processing-tasks/101/artifacts/HY1C_INPUT_VALIDATION_JSON/download"))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"valid\":true}"));
    }

    private ProcessingExecutionController controller(GetTaskDetailUseCase details, DispatchNextStageUseCase dispatch,
                                                     CancelProcessingTaskUseCase cancel, RetryFailedStageUseCase retry) {
        return new ProcessingExecutionController(() -> OWNER, details, dispatch, cancel, retry);
    }

    private ProcessingExecutionController controllerWithDownloads(GetTaskDetailUseCase details, Path workRoot,
                                                                  Path artifactRoot) {
        MultiSourceWorkerProperties properties = properties(workRoot, artifactRoot);
        return new ProcessingExecutionController(() -> OWNER, details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class),
            new LocalWorkerResultArchiveService(properties), new ResultDownloadTicketService());
    }

    private MultiSourceWorkerProperties properties(Path workRoot, Path artifactRoot) {
        MultiSourceWorkerProperties properties = new MultiSourceWorkerProperties();
        properties.setCommand(List.of("worker"));
        properties.setWorkRoot(workRoot);
        properties.setArtifactRoot(artifactRoot);
        properties.setTimeout(Duration.ofMinutes(10));
        return properties;
    }

    private ProcessingExecutionController controllerWithRasterPreview(GetTaskDetailUseCase details,
                                                                       ArtifactRasterPreviewService rasterPreview) {
        return controllerWithRasterPreview(details, rasterPreview, new ArtifactPreviewTicketService());
    }

    private ProcessingExecutionController controllerWithRasterPreview(GetTaskDetailUseCase details,
                                                                       ArtifactRasterPreviewService rasterPreview,
                                                                       ArtifactPreviewTicketService tickets) {
        MultiSourceWorkerProperties properties = new MultiSourceWorkerProperties();
        properties.setCommand(List.of("worker"));
        properties.setWorkRoot(temporaryDirectory.resolve("raster-worker"));
        properties.setArtifactRoot(temporaryDirectory.resolve("raster-artifacts"));
        properties.setTimeout(Duration.ofMinutes(10));
        return new ProcessingExecutionController(() -> OWNER, details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class),
            new LocalWorkerResultArchiveService(properties), new ResultDownloadTicketService(), null,
            tickets, rasterPreview);
    }

    private TaskDetailView detailWithArtifact(Path source, String executionId) throws Exception {
        return new TaskDetailView(TASK_ID, 33L, TaskLifecycleStatus.FAILED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.SUCCEEDED, 1,
                executionId, null, null, null, null)),
            List.of(new TaskArtifactView(STAGE_ID, "HY1C_INPUT_VALIDATION_JSON",
                new StorageUri("rs://output/7/task-01/validation/source.json"), checksum(source), Files.size(source),
                "INTERMEDIATE")));
    }

    private TaskArtifactView artifact(String type, String fileName) {
        return new TaskArtifactView(STAGE_ID, type,
            new StorageUri("rs://output/7/task-01/" + fileName), "sha256:test", 32L, "INTERMEDIATE");
    }

    private TaskDetailView succeededDetailWithDeclaredArtifact() {
        return new TaskDetailView(TASK_ID, 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage(
                "READ", "READ_SOURCE", List.of(), List.of(),
                List.of(new com.htht.multisource.application.port.out.model.StageOutput(
                    "PROCESSING_SUMMARY_JSON", "summary.json",
                    com.htht.multisource.application.port.out.model.StageOutput.Retention.INTERMEDIATE
                )), "{}"
            ))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.SUCCEEDED, 1)),
            List.of(artifact("PROCESSING_SUMMARY_JSON", "summary.json")));
    }

    private static String checksum(Path path) throws Exception {
        return "sha256:" + java.util.HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        );
    }

    private TaskDetailView detail() {
        return new TaskDetailView(TASK_ID, 33L, TaskLifecycleStatus.RUNNING, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))), List.of(),
            List.of(new TaskStageView(STAGE_ID, "READ", StageExecutionStatus.RUNNING, 1)), List.of());
    }
}
