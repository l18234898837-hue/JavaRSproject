package com.htht.multisource.domain.model;

public record AccessSubject(long userId) {

    public AccessSubject {
        if (userId <= 0) {
            throw new IllegalArgumentException("User id must be positive.");
        }
    }
}
