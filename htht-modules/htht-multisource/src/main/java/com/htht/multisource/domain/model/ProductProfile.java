package com.htht.multisource.domain.model;

import java.util.Objects;

public record ProductProfile(
    ProductProfileKey key,
    String displayName,
    ProductDataShape dataShape,
    ProductSupportStatus supportStatus
) {

    public ProductProfile {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(dataShape, "dataShape");
        Objects.requireNonNull(supportStatus, "supportStatus");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Product profile display name must not be blank.");
        }
    }
}
