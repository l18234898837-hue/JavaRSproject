package com.htht.multisource.application.usecase.model;

import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;

public record TaskArtifactView(StageId stageId, String type, StorageUri uri, String checksum) {
}
