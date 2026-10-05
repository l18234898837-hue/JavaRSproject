package com.htht.web.controller.multisource.dto;

/** Source-input-only retention operation. It is intentionally separate from artifact retention. */
public record TaskInputRetentionRequest(String operation, String workspaceKey, String expiresAt,
                                        String taskNo, Long expectedVersion) { }
