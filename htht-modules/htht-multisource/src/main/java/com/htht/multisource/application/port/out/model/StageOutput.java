package com.htht.multisource.application.port.out.model;

import java.util.Objects;

public record StageOutput(String artifactType, String relativePath, Retention retention) {

    public enum Retention {
        INTERMEDIATE,
        FINAL
    }

    public StageOutput {
        Objects.requireNonNull(artifactType, "artifactType");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(retention, "retention");
        if (artifactType.isBlank()) {
            throw new IllegalArgumentException("Stage output artifact type must not be blank.");
        }
        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Stage output relative path is invalid.");
        }
        for (String segment : relativePath.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Stage output relative path is invalid.");
            }
        }
    }
}
