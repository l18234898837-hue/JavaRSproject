package com.htht.multisource.infrastructure.worker;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record WorkerAssetManifest(
    String requestId,
    String grantReference,
    Instant expiresAt,
    List<Input> inputs,
    Path outputPath
) {
    public WorkerAssetManifest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(grantReference, "grantReference");
        Objects.requireNonNull(expiresAt, "expiresAt");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        Objects.requireNonNull(outputPath, "outputPath");
    }

    public record Input(
        String storageUri,
        String role,
        int ordinal,
        Path path,
        String originalFileName,
        String checksum,
        boolean required
    ) {
        public Input {
            Objects.requireNonNull(storageUri, "storageUri");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(originalFileName, "originalFileName");
            Objects.requireNonNull(checksum, "checksum");
        }
    }
}
