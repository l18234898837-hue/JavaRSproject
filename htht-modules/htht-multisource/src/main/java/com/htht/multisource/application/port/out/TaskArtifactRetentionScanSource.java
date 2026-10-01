package com.htht.multisource.application.port.out;

import com.htht.multisource.application.usecase.model.TaskArtifactRetentionScanCandidate;

import java.util.List;

/** Read-only source of completed tasks that do not yet have a retention record. */
@FunctionalInterface
public interface TaskArtifactRetentionScanSource {
    List<TaskArtifactRetentionScanCandidate> findWithoutRetention();
}
