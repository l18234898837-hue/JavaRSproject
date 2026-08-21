package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.model.ExecutionEvent;
import com.htht.multisource.application.port.out.model.ArtifactCandidate;

import java.util.List;

public interface AcceptWorkerEventUseCase {

    Result accept(ExecutionEvent event);

    Result accept(ExecutionEvent event, List<ArtifactCandidate> artifacts);

    enum Result {
        APPLIED,
        IGNORED,
        DUPLICATE
    }
}
