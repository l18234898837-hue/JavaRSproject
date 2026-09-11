package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.InputAsset;

import java.util.Objects;

/** A single authorized TIFF artifact whose safe map metadata is requested. */
public record ArtifactRasterInspectionRequest(String format, String artifactType, InputAsset artifact,
                                             AssetAccessGrant readGrant) {
    public ArtifactRasterInspectionRequest {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(artifactType, "artifactType");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(readGrant, "readGrant");
        if (format.isBlank() || artifactType.isBlank()) {
            throw new IllegalArgumentException("Raster artifact format and type are required.");
        }
    }
}
