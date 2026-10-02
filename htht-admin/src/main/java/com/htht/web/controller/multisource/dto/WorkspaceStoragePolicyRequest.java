package com.htht.web.controller.multisource.dto;

public record WorkspaceStoragePolicyRequest(Integer inputRetentionDays, Integer orphanUploadTtlHours,
                                            Integer workerDirectoryTtlHours, Boolean autoCleanupEnabled,
                                            Long expectedVersion) { }
