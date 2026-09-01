package com.htht.web.controller.multisource.dto;

import java.time.Instant;
import java.util.List;

public record PreflightResponse(
    String rulePackageVersion,
    String preflightToken,
    Instant expiresAt,
    List<CandidateResponse> candidates
) {
    public record CandidateResponse(
        String id,
        String packageId,
        String supportStatus,
        double confidence,
        ProfileKeyResponse profileKey
    ) {
    }

    public record ProfileKeyResponse(String productCode, String profileVersion) {
    }
}
