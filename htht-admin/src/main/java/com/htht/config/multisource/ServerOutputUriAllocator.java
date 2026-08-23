package com.htht.config.multisource;

import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.StorageUri;

import java.util.Objects;
import java.util.function.Supplier;

public final class ServerOutputUriAllocator {
    private final Supplier<String> requestIdGenerator;

    public ServerOutputUriAllocator(Supplier<String> requestIdGenerator) {
        this.requestIdGenerator = Objects.requireNonNull(requestIdGenerator, "requestIdGenerator");
    }

    public StorageUri allocate(AccessSubject subject) {
        Objects.requireNonNull(subject, "subject");
        String requestId = requestIdGenerator.get();
        if (requestId == null || !requestId.matches("[A-Za-z0-9][A-Za-z0-9-]*")) {
            throw new IllegalStateException("Generated output request id is invalid.");
        }
        return new StorageUri("rs://output/" + subject.userId() + "/" + requestId);
    }
}
