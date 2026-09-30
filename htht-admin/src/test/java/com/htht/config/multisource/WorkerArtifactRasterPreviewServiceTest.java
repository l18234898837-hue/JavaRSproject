package com.htht.config.multisource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class WorkerArtifactRasterPreviewServiceTest {

    @Test
    void invalidatingOneTaskKeepsAnotherTasksIdenticalChecksumTileCached() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RasterArtifactInspection inspection = mock(RasterArtifactInspection.class);
        when(inspection.inspectRaster(any(), any(), any(), anyString())).thenReturn(objectMapper.readTree(
            "{\"kind\":\"raster\",\"renderable\":true,\"crs\":\"EPSG:4326\",\"bounds\":[0,0,1,1],\"minZoom\":0,\"maxZoom\":1,\"style\":{\"bands\":[1]}}"));
        when(inspection.renderRasterTile(any(), any(), any(), anyString(), anyInt(), anyInt(), anyInt(), any())).thenReturn(
            objectMapper.readTree("{\"contentType\":\"image/png\",\"pngBase64\":\"iVBORw0KGgo=\"}"));
        WorkerArtifactRasterPreviewService service = new WorkerArtifactRasterPreviewService(inspection, objectMapper);
        AccessSubject subject = new AccessSubject(7L);
        TaskArtifactView artifact = new TaskArtifactView(new StageId(1L), "COG", new StorageUri("rs://output/7/task/result.tif"),
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 1L, "FINAL", "101/output/result.tif");
        TaskDetailView firstTask = detail(TaskId.of(101L));
        TaskDetailView secondTask = detail(TaskId.of(202L));

        ArtifactRasterPreviewService.RasterPreview firstPreview = service.describe(firstTask, subject, artifact, "COG");
        ArtifactRasterPreviewService.RasterPreview secondPreview = service.describe(secondTask, subject, artifact, "COG");
        service.renderTile(firstTask, subject, artifact, "COG", firstPreview, 1, 0, 0);
        service.renderTile(secondTask, subject, artifact, "COG", secondPreview, 1, 0, 0);

        service.invalidateTask(101L);

        service.renderTile(firstTask, subject, artifact, "COG", firstPreview, 1, 0, 0);
        service.renderTile(secondTask, subject, artifact, "COG", secondPreview, 1, 0, 0);

        verify(inspection, times(3)).renderRasterTile(any(), any(), any(), anyString(), anyInt(), anyInt(), anyInt(), any());
    }

    private static TaskDetailView detail(TaskId taskId) {
        return new TaskDetailView(taskId, 1L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-v3", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))),
            List.of(), List.of(), List.of());
    }
}
