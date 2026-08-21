package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.ExecutionId;

public interface ArtifactValidator {

    void validate(TaskId taskId, StageId stageId, ExecutionId executionId, StorageUri outputUri,
                  ArtifactCandidate candidate);
}
