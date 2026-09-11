package com.htht.multisource.application.port.out.model;

import java.util.Map;
import java.util.Objects;

/** A bounded web-map tile rendering request for one authorized raster artifact. */
public record ArtifactRasterTileRequest(ArtifactRasterInspectionRequest raster, int z, int x, int y,
                                        Map<String, Object> style) {
    public ArtifactRasterTileRequest {
        Objects.requireNonNull(raster, "raster");
        style = Map.copyOf(Objects.requireNonNull(style, "style"));
        if (z < 0 || z > 22 || x < 0 || y < 0 || x >= (1 << z) || y >= (1 << z)) {
            throw new IllegalArgumentException("Raster tile coordinate is invalid.");
        }
        if (!(style.get("kind") instanceof String kind) || kind.isBlank()) {
            throw new IllegalArgumentException("Raster tile style is invalid.");
        }
    }
}
