package com.htht.web.controller.multisource.dto;

public record CreateTaskRequest(
    String name,
    String preflightToken,
    String candidateId,
    String parameterSnapshot
) {

    public CreateTaskRequest {
        requireText(name, "name");
        requireText(preflightToken, "preflightToken");
        requireText(candidateId, "candidateId");
        requireText(parameterSnapshot, "parameterSnapshot");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
