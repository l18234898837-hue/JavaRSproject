package com.htht.web.controller.multisource.dto;

import java.util.List;

/** Browser-safe raster tile descriptor. It deliberately excludes storage locations and worker paths. */
public record ArtifactTilePreviewResponse(
    String tileUrl,
    List<Double> bounds,
    String crs,
    Integer minZoom,
    Integer maxZoom,
    Object legend
) {
}
