package com.htht.multisource.domain.model;

import java.util.Objects;

public record TaskId(long value) {

    public TaskId {
        if (value <= 0) {
            throw new IllegalArgumentException("Task id must be positive.");
        }
    }

    public static TaskId of(long value) {
        return new TaskId(value);
    }
}
