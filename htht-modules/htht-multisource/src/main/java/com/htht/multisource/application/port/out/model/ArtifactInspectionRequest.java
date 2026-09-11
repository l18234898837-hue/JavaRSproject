package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.InputAsset;

import java.util.Objects;

public record ArtifactInspectionRequest(String format, InputAsset artifact, AssetAccessGrant readGrant) {
    public ArtifactInspectionRequest {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(readGrant, "readGrant");
        if (format.isBlank()) {
            throw new IllegalArgumentException("Artifact inspection format is required.");
        }
    }
}
