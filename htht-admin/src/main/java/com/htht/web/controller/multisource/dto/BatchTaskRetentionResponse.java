package com.htht.web.controller.multisource.dto;

import java.util.List;

public record BatchTaskRetentionResponse(List<TaskResult> results) {
    public BatchTaskRetentionResponse { results = List.copyOf(results == null ? List.of() : results); }
    public record TaskResult(long taskId, String outcome, String reason) { }
}
