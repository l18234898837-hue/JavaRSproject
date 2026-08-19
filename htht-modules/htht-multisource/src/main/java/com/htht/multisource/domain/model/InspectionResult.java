package com.htht.multisource.domain.model;

import java.util.Objects;

public record InspectionResult(
    ProductProfileKey profileKey,
    ProductDataShape dataShape,
    ProductSupportStatus supportStatus,
    double confidence
) {

    public InspectionResult {
        Objects.requireNonNull(profileKey, "profileKey");
        Objects.requireNonNull(dataShape, "dataShape");
        Objects.requireNonNull(supportStatus, "supportStatus");
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Inspection confidence must be between 0 and 1.");
        }
    }
}
