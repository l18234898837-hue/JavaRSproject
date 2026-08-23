package com.htht.multisource.infrastructure.persistence.id;

import com.htht.multisource.application.port.out.ProcessingBatchIdGenerator;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class LongSupplierProcessingBatchIdGenerator implements ProcessingBatchIdGenerator {

    private final LongSupplier supplier;

    public LongSupplierProcessingBatchIdGenerator(LongSupplier supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public long nextId() {
        return PositiveIdSupplier.next(supplier);
    }
}
