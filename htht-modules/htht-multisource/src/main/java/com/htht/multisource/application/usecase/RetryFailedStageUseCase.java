package com.htht.multisource.application.usecase;

import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.AccessSubject;

public interface RetryFailedStageUseCase {

    void retry(StageId stageId, AccessSubject subject);
}
