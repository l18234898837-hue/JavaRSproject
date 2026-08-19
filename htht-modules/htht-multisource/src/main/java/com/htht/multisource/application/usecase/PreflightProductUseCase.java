package com.htht.multisource.application.usecase;

import com.htht.multisource.application.usecase.model.PreflightResult;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.InputAsset;

import java.util.List;

public interface PreflightProductUseCase {

    PreflightResult preflight(AccessSubject subject, List<InputAsset> inputs);
}
