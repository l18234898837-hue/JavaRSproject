package com.htht.multisource.domain.model;

import java.util.List;
import java.util.Objects;

public record ProductPackage(
    String packageId,
    ProductProfileKey profileKey,
    ProductDataShape dataShape,
    List<InputAsset> files
) {

    public ProductPackage {
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(profileKey, "profileKey");
        Objects.requireNonNull(dataShape, "dataShape");
        Objects.requireNonNull(files, "files");
        files = List.copyOf(files);
        if (packageId.isBlank()) {
            throw new IllegalArgumentException("Product package id must not be blank.");
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Product package files must not be empty.");
        }
    }
}
