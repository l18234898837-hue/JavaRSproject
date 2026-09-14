package com.htht.web.controller.multisource;

import com.fasterxml.jackson.databind.JsonNode;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;

@FunctionalInterface
public interface QualityArtifactInspector {
    JsonNode inspect(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact, String format);
}
