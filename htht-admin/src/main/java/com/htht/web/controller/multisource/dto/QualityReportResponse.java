package com.htht.web.controller.multisource.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QualityReportResponse(
    String reportId,
    long taskId,
    Instant generatedAt,
    String overallStatus,
    Summary summary,
    List<String> selectedFunctions,
    List<Check> checks,
    List<ArtifactReference> artifactReferences,
    String ruleVersion
) {
    public record Summary(int totalChecks, int passedChecks, int warningChecks, int failedChecks) {}

    public record Check(
        String checkId,
        String functionKey,
        Long stageId,
        String artifactType,
        String name,
        String status,
        String actualValue,
        String threshold,
        String unit,
        String userMessage,
        String technicalMessage,
        String severity,
        Map<String, Object> details,
        String availability,
        String recommendation
    ) {
        public Check(
            String checkId,
            String functionKey,
            Long stageId,
            String artifactType,
            String name,
            String status,
            String actualValue,
            String threshold,
            String unit,
            String userMessage,
            String technicalMessage,
            String severity,
            Map<String, Object> details
        ) {
            this(checkId, functionKey, stageId, artifactType, name, status, actualValue, threshold, unit,
                userMessage, technicalMessage, severity, details, "CHECKED", null);
        }
    }

    public record ArtifactReference(
        Long stageId,
        String type,
        String displayName,
        String format,
        long sizeBytes,
        String checksum,
        String retention
    ) {}
}
