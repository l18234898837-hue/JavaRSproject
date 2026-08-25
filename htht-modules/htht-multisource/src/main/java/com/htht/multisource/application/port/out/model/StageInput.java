package com.htht.multisource.application.port.out.model;

import java.util.Objects;

public record StageInput(String key, Source source, String artifactType, boolean required) {

    public enum Source {
        RAW_INPUT,
        UPSTREAM_ARTIFACT
    }

    public StageInput {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(artifactType, "artifactType");
        if (key.isBlank() || artifactType.isBlank()) {
            throw new IllegalArgumentException("Stage input key and artifact type must not be blank.");
        }
    }
}
