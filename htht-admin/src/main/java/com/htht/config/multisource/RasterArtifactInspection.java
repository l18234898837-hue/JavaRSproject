package com.htht.config.multisource;

import com.fasterxml.jackson.databind.JsonNode;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;

import java.util.Map;

/** Bounded worker operations needed by raster preview metadata and tiles. */
public interface RasterArtifactInspection {

    JsonNode inspectRaster(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact, String format);

    JsonNode renderRasterTile(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact,
                              String format, int z, int x, int y, Map<String, Object> style);
}
