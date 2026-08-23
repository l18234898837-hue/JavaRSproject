package com.htht.web.controller.multisource.dto;

/**
 * A client-side reference to a file already managed by the system file service.
 */
public record AssetRequest(
    String sourceType,
    Long sourceId,
    String role,
    Integer ordinal,
    Boolean required
) {

    public AssetRequest {
        if (!"SYS_FILE".equals(sourceType)) {
            throw new IllegalArgumentException("sourceType must be SYS_FILE");
        }
        if (sourceId == null || sourceId <= 0) {
            throw new IllegalArgumentException("sourceId must be positive");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        if (ordinal == null || ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        if (required == null) {
            throw new IllegalArgumentException("required must not be null");
        }
    }
}
