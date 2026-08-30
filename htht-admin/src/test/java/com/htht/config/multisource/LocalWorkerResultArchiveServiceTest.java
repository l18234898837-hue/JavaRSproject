package com.htht.config.multisource;

import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskStageView;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class LocalWorkerResultArchiveServiceTest {

    @TempDir
    Path workRoot;

    @Test
    void streamsArtifactsWithoutCreatingAnotherLargeArchive() throws Exception {
        byte[] content = "already-compressed-artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path artifactFile = workRoot.resolve("execution-1/output/standard/result.tif");
        Files.createDirectories(artifactFile.getParent());
        Files.write(artifactFile, content);

        TaskId taskId = TaskId.of(101L);
        StageId stageId = new StageId(201L);
        String checksum = "sha256:" + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(content));
        TaskDetailView detail = new TaskDetailView(
            taskId, 1L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.PASS,
            new ProcessingPlan("plan-v2", List.of(
                new ProcessingStage("STANDARDIZE", "STANDARDIZE", List.of()))), List.of(),
            List.of(new TaskStageView(stageId, "STANDARDIZE", StageExecutionStatus.SUCCEEDED, 1,
                "execution-1", null, null)),
            List.of(new TaskArtifactView(stageId, "STANDARD_COG",
                new StorageUri("rs://output/7/output-request/standard/result.tif"), checksum))
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        service().write(detail, new AccessSubject(7L), "fy3h-mersi-result.zip", response);

        assertThat(response.getContentType()).isEqualTo("application/zip");
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getHeader("Content-Disposition"))
            .isEqualTo("attachment; filename=\"fy3h-mersi-result.zip\"");
        assertThat(workRoot.resolve("archives")).doesNotExist();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("result.tif");
            assertThat(zip.readAllBytes()).isEqualTo(content);
            assertThat(zip.getNextEntry()).isNull();
        }
    }

    private LocalWorkerResultArchiveService service() {
        MultiSourceWorkerProperties properties = new MultiSourceWorkerProperties();
        properties.setCommand(List.of("worker"));
        properties.setWorkRoot(workRoot);
        properties.setTimeout(Duration.ofMinutes(1));
        return new LocalWorkerResultArchiveService(properties);
    }
}
