package com.htht.multisource.application.usecase;
import com.htht.multisource.application.usecase.model.ArtifactRetentionOperation;
import com.htht.multisource.domain.model.*;
public interface TaskArtifactRetentionUseCase { TaskArtifactRetention change(TaskId taskId, AccessSubject subject, ArtifactRetentionOperation operation); }
