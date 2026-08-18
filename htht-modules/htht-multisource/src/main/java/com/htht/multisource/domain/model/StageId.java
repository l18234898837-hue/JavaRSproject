package com.htht.multisource.domain.model;

public record StageId(long value) {

    public StageId {
        if (value <= 0) {
            throw new IllegalArgumentException("Stage id must be positive.");
        }
    }
}
