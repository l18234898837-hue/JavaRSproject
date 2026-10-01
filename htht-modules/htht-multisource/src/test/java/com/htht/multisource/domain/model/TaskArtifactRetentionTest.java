package com.htht.multisource.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TaskArtifactRetentionTest {

    private static final Instant NOW = Instant.parse("2026-09-30T08:00:00Z");
    private static final AccessSubject OWNER = new AccessSubject(7L);

    @Test
    void recyclingPinnedRetentionClearsPinAndCanRestoreAvailable() {
        TaskArtifactRetention pinned = TaskArtifactRetention.available(TaskId.of(12L)).pin(OWNER, NOW);

        TaskArtifactRetention recycled = pinned.recycle(OWNER, NOW);

        assertThat(recycled.status()).isEqualTo(TaskArtifactRetentionStatus.RECYCLE_BIN);
        assertThat(recycled.pinned()).isFalse();
        assertThat(recycled.recycledAt()).isEqualTo(NOW);
        assertThat(recycled.restore(OWNER, NOW).status()).isEqualTo(TaskArtifactRetentionStatus.AVAILABLE);
        assertThat(recycled.restore(OWNER, NOW).recycledAt()).isNull();
    }

    @Test
    void purgeOnlyStartsFromRecycleBinOrRetryableFailure() {
        TaskArtifactRetention recycled = TaskArtifactRetention.available(TaskId.of(12L)).recycle(OWNER, NOW);

        TaskArtifactRetention purging = recycled.beginPurge(OWNER, NOW);

        assertThat(purging.status()).isEqualTo(TaskArtifactRetentionStatus.PURGING);
        assertThat(purging.purgeStartedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> TaskArtifactRetention.available(TaskId.of(12L)).beginPurge(OWNER, NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completedPurgeCannotBeRestoredOrRecycled() {
        TaskArtifactRetention purged = TaskArtifactRetention.available(TaskId.of(12L))
            .recycle(OWNER, NOW)
            .beginPurge(OWNER, NOW)
            .purgeSucceeded(OWNER, NOW);

        assertThat(purged.status()).isEqualTo(TaskArtifactRetentionStatus.PURGED);
        assertThat(purged.purgedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> purged.restore(OWNER, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> purged.recycle(OWNER, NOW)).isInstanceOf(IllegalStateException.class);
    }
}
