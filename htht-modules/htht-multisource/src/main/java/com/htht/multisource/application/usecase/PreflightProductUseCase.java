package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.model.InspectionResult;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.InputAsset;

import java.util.List;

public interface PreflightProductUseCase {

    InspectionResult preflight(AccessSubject subject, List<InputAsset> inputs);
}
