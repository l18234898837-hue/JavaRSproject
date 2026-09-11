package com.htht.config.multisource;

import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;

import java.util.List;
import java.util.Map;

public interface ArtifactRasterPreviewService {
    RasterPreview describe(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact, String format);

    byte[] renderTile(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact, String format,
                      RasterPreview preview, int z, int x, int y);

    record RasterPreview(boolean renderable, String reason, String crs, List<Double> bounds,
                         Integer minZoom, Integer maxZoom, Map<String, Object> style, Object legend) {
    }
}
