package com.htht.multisource.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@Tag("dev")
class TaskInputRetentionTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final AccessSubject OPERATOR = new AccessSubject(7);

    @Test void scheduledRetentionIsImmutableAndIncrementsVersion() {
        TaskInputRetention original = TaskInputRetention.available(TaskId.of(1), "default", NOW);
        TaskInputRetention next = original.schedule(NOW.plusSeconds(3600), OPERATOR, NOW);
        assertThat(original.status()).isEqualTo(TaskInputRetentionStatus.AVAILABLE);
        assertThat(next.status()).isEqualTo(TaskInputRetentionStatus.SCHEDULED);
        assertThat(next.version()).isEqualTo(1);
    }

    @Test void purgedIsTerminal() {
        TaskInputRetention purged = TaskInputRetention.available(TaskId.of(1), "default", NOW)
            .schedule(NOW, OPERATOR, NOW)
            .claim(NOW)
            .purgeSucceeded(OPERATOR, NOW);
        assertThatIllegalStateException().isThrownBy(() -> purged.claim(NOW.plusSeconds(1)));
    }

    @Test void recycleBinRetentionCanBeClaimedForConfirmedPhysicalPurge() {
        TaskInputRetention recycled = TaskInputRetention.available(TaskId.of(2), "default", NOW)
            .recycle(OPERATOR, NOW, NOW.plusSeconds(14 * 86400L));

        TaskInputRetention purging = recycled.beginPurge(OPERATOR, NOW.plusSeconds(1));

        assertThat(purging.status()).isEqualTo(TaskInputRetentionStatus.PURGING);
        assertThat(purging.purgeStartedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}
