package com.htht.web.controller.multisource.dto;

import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskInputRetention;

public record TaskHistoryDetailResponse(TaskDetailView detail, TaskArtifactRetention retention, long artifactSizeBytes,
                                        TaskInputRetention sourceRetention) {
    public TaskHistoryDetailResponse(TaskDetailView detail, TaskArtifactRetention retention, long artifactSizeBytes) {
        this(detail, retention, artifactSizeBytes, null);
    }
}
