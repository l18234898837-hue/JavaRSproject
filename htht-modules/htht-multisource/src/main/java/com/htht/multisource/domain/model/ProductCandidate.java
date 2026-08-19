package com.htht.multisource.domain.model;

import java.util.List;
import java.util.Objects;

public record ProductCandidate(
    String packageId,
    ProductProfileKey profileKey,
    ProductDataShape dataShape,
    ProductSupportStatus supportStatus,
    ProductPackage productPackage,
    double confidence
) {

    public ProductCandidate {
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(profileKey, "profileKey");
        Objects.requireNonNull(dataShape, "dataShape");
        Objects.requireNonNull(supportStatus, "supportStatus");
        Objects.requireNonNull(productPackage, "productPackage");
        if (packageId.isBlank()) {
            throw new IllegalArgumentException("Product candidate package id must not be blank.");
        }
        if (!packageId.equals(productPackage.packageId())
            || !profileKey.equals(productPackage.profileKey())
            || dataShape != productPackage.dataShape()) {
            throw new IllegalArgumentException("Product candidate and package metadata must match.");
        }
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Product candidate confidence must be between 0 and 1.");
        }
    }

    public ProductCandidate(
        String packageId,
        ProductProfileKey profileKey,
        ProductDataShape dataShape,
        ProductSupportStatus supportStatus,
        List<InputAsset> files,
        double confidence
    ) {
        this(
            packageId,
            profileKey,
            dataShape,
            supportStatus,
            new ProductPackage(packageId, profileKey, dataShape, files),
            confidence
        );
    }

    public List<InputAsset> files() {
        return productPackage.files();
    }
}
