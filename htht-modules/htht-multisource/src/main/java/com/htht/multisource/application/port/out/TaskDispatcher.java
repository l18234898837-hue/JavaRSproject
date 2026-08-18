package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.DispatchReceipt;
import com.htht.multisource.application.port.out.model.StageExecutionRequest;
import com.htht.multisource.domain.model.ExecutionId;

public interface TaskDispatcher {

    DispatchReceipt dispatch(StageExecutionRequest request);

    void cancel(ExecutionId executionId);
}
