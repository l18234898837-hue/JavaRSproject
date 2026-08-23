package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ProductInspector;
import com.htht.multisource.application.port.out.model.InspectionRequest;
import com.htht.multisource.application.port.out.model.InspectionResponse;
import com.htht.multisource.application.usecase.PreflightProductUseCase;
import com.htht.multisource.application.usecase.model.PreflightResult;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.InputAsset;

import java.util.List;
import java.util.Objects;

public final class PreflightProductService implements PreflightProductUseCase {

    private final ProductInspector inspector;
    private final AssetStorage assetStorage;

    public PreflightProductService(ProductInspector inspector, AssetStorage assetStorage) {
        this.inspector = Objects.requireNonNull(inspector, "inspector");
        this.assetStorage = Objects.requireNonNull(assetStorage, "assetStorage");
    }

    @Override
    public PreflightResult preflight(AccessSubject subject, List<InputAsset> inputs) {
        Objects.requireNonNull(subject, "subject");
        List<InputAsset> copiedInputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        InspectionResponse response = inspector.inspect(
            new InspectionRequest(subject, copiedInputs, assetStorage.grantRead(subject, copiedInputs))
        );
        return new PreflightResult(response.rulePackageVersion(), response.candidates());
    }
}
