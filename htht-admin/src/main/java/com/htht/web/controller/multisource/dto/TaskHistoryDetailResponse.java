package com.htht.web.controller.multisource.dto;

import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.TaskArtifactRetention;

public record TaskHistoryDetailResponse(TaskDetailView detail, TaskArtifactRetention retention, long artifactSizeBytes) {
}
