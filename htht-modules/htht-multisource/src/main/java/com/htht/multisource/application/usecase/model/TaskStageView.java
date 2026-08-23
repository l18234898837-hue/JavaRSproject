package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;

public record TaskStageView(StageId id, String stageKey, StageExecutionStatus status, int attempt) {
}
