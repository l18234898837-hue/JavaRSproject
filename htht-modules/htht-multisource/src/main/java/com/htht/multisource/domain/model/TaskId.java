package com.htht.multisource.domain.model;

import com.fasterxml.jackson.annotation.JsonValue;

public record TaskId(long value) {

    public TaskId {
        if (value <= 0) {
            throw new IllegalArgumentException("Task id must be positive.");
        }
    }

    public static TaskId of(long value) {
        return new TaskId(value);
    }

    /**
     * Task ids cross the HTTP boundary and are used directly in path URLs.
     * Serialize the scalar value instead of the record wrapper so clients do
     * not turn an id into the literal string "[object Object]".
     */
    @Override
    @JsonValue
    public long value() {
        return value;
    }
}
