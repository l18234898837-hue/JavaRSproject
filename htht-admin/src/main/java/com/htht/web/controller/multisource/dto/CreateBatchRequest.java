package com.htht.web.controller.multisource.dto;

import java.util.List;
import java.util.Objects;

public record CreateBatchRequest(
    String name,
    String preflightToken,
    List<String> candidateIds,
    String parameterSnapshot
) {

    public CreateBatchRequest {
        requireText(name, "name");
        requireText(preflightToken, "preflightToken");
        candidateIds = List.copyOf(Objects.requireNonNull(candidateIds, "candidateIds"));
        if (candidateIds.isEmpty() || candidateIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("candidateIds must contain nonblank ids");
        }
        requireText(parameterSnapshot, "parameterSnapshot");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
