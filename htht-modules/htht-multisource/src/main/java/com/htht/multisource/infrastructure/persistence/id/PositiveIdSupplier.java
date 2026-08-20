package com.htht.multisource.infrastructure.persistence.id;

import java.util.function.LongSupplier;

final class PositiveIdSupplier {

    private PositiveIdSupplier() {
    }

    static long next(LongSupplier supplier) {
        long value = supplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("Generated identifier must be positive.");
        }
        return value;
    }
}
