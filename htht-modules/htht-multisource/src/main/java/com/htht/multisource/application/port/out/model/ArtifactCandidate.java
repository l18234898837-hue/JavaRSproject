package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.StorageUri;

import java.util.Objects;

public record ArtifactCandidate(StorageUri uri, String checksum, String type, long sizeBytes) {

    public ArtifactCandidate {
        Objects.requireNonNull(uri, "uri");
        if (checksum == null || checksum.isBlank() || type == null || type.isBlank() || sizeBytes < 0) {
            throw new IllegalArgumentException("Artifact candidate is invalid.");
        }
    }
}
