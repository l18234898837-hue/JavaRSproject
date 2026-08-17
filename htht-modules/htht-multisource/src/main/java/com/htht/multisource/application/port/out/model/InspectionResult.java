package com.htht.multisource.application.port.out.model;

public record InspectionResult(
    String profileCode,
    String profileVersion,
    String rulePackageVersion,
    double confidence
) {
}
