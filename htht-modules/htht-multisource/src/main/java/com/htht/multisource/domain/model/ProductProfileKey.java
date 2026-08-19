package com.htht.multisource.domain.model;

import java.util.Objects;

public record ProductProfileKey(String productCode, String profileVersion) {

    public ProductProfileKey {
        Objects.requireNonNull(productCode, "productCode");
        Objects.requireNonNull(profileVersion, "profileVersion");
        if (productCode.isBlank() || profileVersion.isBlank()) {
            throw new IllegalArgumentException("Product profile key values must not be blank.");
        }
    }
}
