package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.TaskArtifactAvailabilityProbe;
import com.htht.multisource.application.port.out.TaskArtifactRetentionRepository;
import com.htht.multisource.application.port.out.TaskArtifactRetentionScanSource;
import com.htht.multisource.application.usecase.TaskArtifactRetentionScanUseCase;
import com.htht.multisource.application.usecase.model.TaskArtifactRetentionScanCandidate;
import com.htht.multisource.application.usecase.model.TaskArtifactRetentionScanResult;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;

import java.util.Objects;

/** Initializes retention records from storage presence only; it never recovers or deletes files. */
public final class TaskArtifactRetentionScanService implements TaskArtifactRetentionScanUseCase {
    private final TaskArtifactRetentionScanSource source;
    private final TaskArtifactRetentionRepository retentionRepository;
    private final TaskArtifactAvailabilityProbe availabilityProbe;

    public TaskArtifactRetentionScanService(TaskArtifactRetentionScanSource source,
                                            TaskArtifactRetentionRepository retentionRepository,
                                            TaskArtifactAvailabilityProbe availabilityProbe) {
        this.source = Objects.requireNonNull(source, "source");
        this.retentionRepository = Objects.requireNonNull(retentionRepository, "retentionRepository");
        this.availabilityProbe = Objects.requireNonNull(availabilityProbe, "availabilityProbe");
    }

    @Override
    public TaskArtifactRetentionScanResult scan() {
        long createdAvailable = 0;
        long createdMissing = 0;
        long skippedExisting = 0;
        long unreadableMetadata = 0;
        for (TaskArtifactRetentionScanCandidate candidate : source.findWithoutRetention()) {
            TaskArtifactRetentionStatus status;
            try {
                status = availabilityProbe.probe(candidate);
                if (status != TaskArtifactRetentionStatus.AVAILABLE && status != TaskArtifactRetentionStatus.MISSING) {
                    throw new IllegalArgumentException("Availability probe returned an invalid retention status.");
                }
            } catch (RuntimeException exception) {
                unreadableMetadata++;
                continue;
            }
            TaskArtifactRetention retention = status == TaskArtifactRetentionStatus.AVAILABLE
                ? TaskArtifactRetention.available(candidate.taskId())
                : new TaskArtifactRetention(candidate.taskId(), TaskArtifactRetentionStatus.MISSING, false,
                    null, null, null, null, null, 0L);
            if (!retentionRepository.createIfAbsent(retention)) {
                skippedExisting++;
            } else if (status == TaskArtifactRetentionStatus.AVAILABLE) {
                createdAvailable++;
            } else {
                createdMissing++;
            }
        }
        return new TaskArtifactRetentionScanResult(createdAvailable, createdMissing, skippedExisting, unreadableMetadata);
    }
}
