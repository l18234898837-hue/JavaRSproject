package com.htht.multisource.infrastructure.persistence.id;

import com.htht.multisource.application.port.out.StageIdGenerator;
import com.htht.multisource.domain.model.StageId;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class LongSupplierStageIdGenerator implements StageIdGenerator {

    private final LongSupplier supplier;

    public LongSupplierStageIdGenerator(LongSupplier supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public StageId nextId() {
        return new StageId(PositiveIdSupplier.next(supplier));
    }
}
