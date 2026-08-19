package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.StorageUri;

import java.util.List;
import java.util.Objects;

public record BatchCreateCommand(
    String name,
    AccessSubject subject,
    List<ProductCandidate> candidates,
    String parameterSnapshot,
    StorageUri outputUri
) {
    public BatchCreateCommand {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(outputUri, "outputUri");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (name == null || name.isBlank() || candidates.isEmpty()
            || parameterSnapshot == null || parameterSnapshot.isBlank()) {
            throw new IllegalArgumentException("Batch create command is incomplete.");
        }
    }
}
