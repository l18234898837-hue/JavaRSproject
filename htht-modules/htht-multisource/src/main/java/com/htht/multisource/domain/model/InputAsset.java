package com.htht.multisource.domain.model;

import java.util.Objects;

public record InputAsset(
    String role,
    int ordinal,
    AssetReference reference,
    String checksum,
    boolean required
) {
    public InputAsset {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(reference, "reference");
        if (role.isBlank() || ordinal < 0) {
            throw new IllegalArgumentException("Input asset role and ordinal are invalid.");
        }
    }
}
