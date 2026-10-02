package com.htht.web.controller.multisource.dto;

public record WorkspaceStoragePolicyResponse(String workspaceKey, int inputRetentionDays,
                                             int orphanUploadTtlHours, int workerDirectoryTtlHours,
                                             boolean autoCleanupEnabled, long version) { }
