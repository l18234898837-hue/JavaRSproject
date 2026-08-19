package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.ProductCandidate;

import java.util.List;
import java.util.Objects;

public record PreflightResult(
    String rulePackageVersion,
    List<ProductCandidate> candidates
) {
    public PreflightResult {
        if (rulePackageVersion == null || rulePackageVersion.isBlank()) {
            throw new IllegalArgumentException("Rule package version must not be blank.");
        }
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Preflight candidates must not be empty.");
        }
    }
}
