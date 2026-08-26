package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.ArtifactCandidate;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.domain.model.Artifact;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;

import java.nio.file.Path;

public interface TaskArtifactStore {

    Artifact materialize(TaskId taskId, StageId stageId, String stageKey, int attempt, ExecutionId executionId,
                         StorageUri outputUri, ArtifactCandidate candidate, StageOutput output);

    Path resolveForDownload(Artifact artifact);
}
