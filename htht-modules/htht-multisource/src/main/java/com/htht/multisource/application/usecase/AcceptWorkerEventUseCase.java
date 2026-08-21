package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.model.ExecutionEvent;

public interface AcceptWorkerEventUseCase {

    Result accept(ExecutionEvent event);

    enum Result {
        APPLIED,
        IGNORED,
        DUPLICATE
    }
}
