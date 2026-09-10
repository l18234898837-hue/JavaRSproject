package com.htht.web.controller.multisource;

import com.htht.config.multisource.LocalWorkerResultArchiveService;
import com.htht.config.multisource.MultiSourceWorkerProperties;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
        MultiSourceWorkerProperties properties = new MultiSourceWorkerProperties();
        properties.setCommand(List.of("worker"));
        properties.setWorkRoot(workRoot);
        properties.setArtifactRoot(artifactRoot);
        properties.setTimeout(Duration.ofMinutes(10));
        return new ProcessingExecutionController(() -> OWNER, details, mock(DispatchNextStageUseCase.class),
            mock(CancelProcessingTaskUseCase.class), mock(RetryFailedStageUseCase.class),
            new LocalWorkerResultArchiveService(properties), null);
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
