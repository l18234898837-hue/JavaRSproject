package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.StorageUri;

import java.util.Objects;

public record CreateProcessingTaskCommand(
    String name,
    AccessSubject subject,
    ProductCandidate candidate,
    String parameterSnapshot,
    StorageUri outputUri
) {
    public CreateProcessingTaskCommand {
        if (name == null || name.isBlank() || parameterSnapshot == null || parameterSnapshot.isBlank()) {
            throw new IllegalArgumentException("Task create command is incomplete.");
        }
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(outputUri, "outputUri");
    }
}
