package com.htht.config.multisource;

import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskStageView;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalTaskArtifactPurgeServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsTraversalStorageKeyWithoutTouchingUploadFiles() throws Exception {
        Path workRoot = Files.createDirectories(temporaryDirectory.resolve("worker"));
        Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
        Path uploadRoot = Files.createDirectories(temporaryDirectory.resolve("upload"));
        Path original = Files.writeString(uploadRoot.resolve("original.tif"), "original");
        LocalTaskArtifactPurgeService service = new LocalTaskArtifactPurgeService(workRoot, artifactRoot, taskId -> { });

        assertThatThrownBy(() -> service.purge(detailWithStorageKey("../../upload/original.tif")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("storage key");

        assertThat(original).exists();
    }

    private static TaskDetailView detailWithStorageKey(String storageKey) {
        TaskId taskId = TaskId.of(101L);
        return new TaskDetailView(
            taskId,
            12L,
            TaskLifecycleStatus.SUCCEEDED,
            QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-v3", List.of(new ProcessingStage("WRITE", "WRITE_RESULT", List.of()))),
            List.of(),
            List.of(new TaskStageView(new StageId(1L), "WRITE", StageExecutionStatus.SUCCEEDED, 1,
                "execution-1", null, null)),
            List.of(new TaskArtifactView(new StageId(1L), "COG", new StorageUri("rs://output/7/task/cog.tif"),
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 42L, "FINAL", storageKey))
        );
    }
}
