package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProductCandidate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PreflightSession(
    String token,
    Instant expiresAt,
    List<InputAsset> inputs,
    List<Candidate> candidates
) {

    public PreflightSession {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Preflight session token must not be blank.");
        }
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (inputs.isEmpty() || candidates.isEmpty()) {
            throw new IllegalArgumentException("Preflight session requires inputs and candidates.");
        }
    }

    public record Candidate(String id, ProductCandidate candidate) {
        public Candidate {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Preflight candidate id must not be blank.");
            }
            candidate = Objects.requireNonNull(candidate, "candidate");
        }
    }
}
