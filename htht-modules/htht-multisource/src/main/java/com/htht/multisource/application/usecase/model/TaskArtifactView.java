package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;

public record TaskArtifactView(StageId stageId, String type, StorageUri uri, String checksum,
                               long sizeBytes, String retention) {
    public TaskArtifactView(StageId stageId, String type, StorageUri uri, String checksum) {
        this(stageId, type, uri, checksum, 0L, null);
    }
}
