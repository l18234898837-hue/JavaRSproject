package com.htht.multisource.application.usecase;

import com.htht.multisource.application.usecase.model.BatchCreateCommand;
import com.htht.multisource.application.usecase.model.BatchCreationResult;

public interface CreateProcessingBatchUseCase {

    BatchCreationResult create(BatchCreateCommand command);
}
