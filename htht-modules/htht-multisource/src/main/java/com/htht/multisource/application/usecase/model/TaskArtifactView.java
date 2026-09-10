package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;

public record TaskArtifactView(StageId stageId, String type, StorageUri uri, String checksum,
                               long sizeBytes, String retention, String storageKey) {
    public TaskArtifactView(StageId stageId, String type, StorageUri uri, String checksum) {
        this(stageId, type, uri, checksum, 0L, null, null);
    }

    public TaskArtifactView(StageId stageId, String type, StorageUri uri, String checksum,
                            long sizeBytes, String retention) {
        this(stageId, type, uri, checksum, sizeBytes, retention, null);
    }
}
