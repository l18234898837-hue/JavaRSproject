package com.htht.multisource.infrastructure.persistence.id;

import com.htht.multisource.application.port.out.TaskIdGenerator;
import com.htht.multisource.domain.model.TaskId;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class LongSupplierTaskIdGenerator implements TaskIdGenerator {

    private final LongSupplier supplier;

    public LongSupplierTaskIdGenerator(LongSupplier supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public TaskId nextId() {
        return TaskId.of(PositiveIdSupplier.next(supplier));
    }
}
