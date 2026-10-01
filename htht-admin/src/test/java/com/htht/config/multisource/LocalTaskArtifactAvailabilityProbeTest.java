package com.htht.config.multisource;

import com.htht.multisource.application.usecase.model.TaskArtifactRetentionScanCandidate;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalTaskArtifactAvailabilityProbeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void classifiesReadableRegisteredArtifactsAsAvailableAndAbsentArtifactsAsMissing() throws Exception {
        Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
        Files.createDirectories(artifactRoot.resolve("12/output"));
        Files.writeString(artifactRoot.resolve("12/output/result.tif"), "result");
        LocalTaskArtifactAvailabilityProbe probe = new LocalTaskArtifactAvailabilityProbe(artifactRoot);

        assertThat(probe.probe(new TaskArtifactRetentionScanCandidate(TaskId.of(12L),
            List.of("12/output/result.tif")))).isEqualTo(TaskArtifactRetentionStatus.AVAILABLE);
        assertThat(probe.probe(new TaskArtifactRetentionScanCandidate(TaskId.of(13L),
            List.of("13/output/result.tif")))).isEqualTo(TaskArtifactRetentionStatus.MISSING);
    }

    @Test
    void rejectsEscapingStorageKeysWithoutReadingOutsideArtifactRoot() throws Exception {
        Path artifactRoot = Files.createDirectories(temporaryDirectory.resolve("artifacts"));
        Path outside = Files.writeString(temporaryDirectory.resolve("upload-source.tif"), "original");
        LocalTaskArtifactAvailabilityProbe probe = new LocalTaskArtifactAvailabilityProbe(artifactRoot);

        assertThatThrownBy(() -> probe.probe(new TaskArtifactRetentionScanCandidate(TaskId.of(12L),
            List.of("../upload-source.tif"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("storage key");
        assertThat(outside).exists();
    }
}
