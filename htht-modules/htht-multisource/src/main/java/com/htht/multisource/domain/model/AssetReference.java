package com.htht.multisource.domain.model;

import java.util.Objects;

public record AssetReference(String sourceType, String sourceId) {

    public AssetReference {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        if (sourceType.isBlank() || sourceId.isBlank()) {
            throw new IllegalArgumentException("Asset reference values must not be blank.");
        }
    }
}
