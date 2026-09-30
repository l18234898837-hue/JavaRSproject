package com.htht.multisource.application.port.out;

import com.htht.multisource.application.usecase.model.TaskArtifactRetentionScanCandidate;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;

/** Checks whether registered task artifact files are readable without changing storage. */
@FunctionalInterface
public interface TaskArtifactAvailabilityProbe {
    TaskArtifactRetentionStatus probe(TaskArtifactRetentionScanCandidate candidate);
}
