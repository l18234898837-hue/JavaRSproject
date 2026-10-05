package com.htht.web.controller.multisource.dto;

import java.util.List;
import java.util.Map;

public record BatchTaskCleanupRequest(List<Long> taskIds, String operation, String workspaceKey,
                                      String expiresAt, Map<String, String> confirmations) {
    public BatchTaskCleanupRequest {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
        confirmations = confirmations == null ? Map.of() : Map.copyOf(confirmations);
    }
}
