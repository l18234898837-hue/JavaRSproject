package com.htht.multisource.application.port.out.model;

import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.AccessSubject;

import java.util.List;

public record InspectionRequest(AccessSubject subject, List<InputAsset> inputs, AssetAccessGrant readGrant) {

    public InspectionRequest {
        inputs = List.copyOf(inputs);
    }
}
