package com.htht.web.controller.multisource.dto;

import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskInputRetention;

public record TaskHistoryDetailResponse(TaskDetailView detail, TaskArtifactRetention retention, long artifactSizeBytes,
                                        TaskInputRetention sourceRetention, long sourceInputCount, long sourceInputSizeBytes) {
    public TaskHistoryDetailResponse(TaskDetailView detail, TaskArtifactRetention retention, long artifactSizeBytes) {
        this(detail, retention, artifactSizeBytes, null, 0L, 0L);
    }

    public TaskHistoryDetailResponse(TaskDetailView detail, TaskArtifactRetention retention, long artifactSizeBytes,
                                     TaskInputRetention sourceRetention) {
        this(detail, retention, artifactSizeBytes, sourceRetention, 0L, 0L);
    }
}
