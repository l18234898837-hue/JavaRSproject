package com.htht.multisource.domain.model;

import java.util.Objects;

public record StorageUri(String value) {

    public StorageUri {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Storage uri must not be blank.");
        }
    }
}
