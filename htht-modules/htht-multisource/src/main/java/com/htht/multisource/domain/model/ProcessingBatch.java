package com.htht.multisource.domain.model;

import java.util.List;
import java.util.Objects;

public record ProcessingBatch(
    long id,
    String name,
    ProcessingBatchStatus status,
    List<ProductCandidate> candidates
) {

    public ProcessingBatch {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
        if (id <= 0 || name.isBlank()) {
            throw new IllegalArgumentException("Processing batch id and name are invalid.");
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Processing batch candidates must not be empty.");
        }
    }
}
