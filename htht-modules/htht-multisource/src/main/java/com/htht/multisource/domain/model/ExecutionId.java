package com.htht.multisource.domain.model;

import java.util.Objects;

public record ExecutionId(String value) {

    public ExecutionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Execution id must not be blank.");
        }
    }
}
