package com.htht.config.multisource;

import com.htht.multisource.domain.model.AccessSubject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ArtifactPreviewTicketServiceTest {
    private static final AccessSubject OWNER = new AccessSubject(7L);

    @Test
    void validatesAReusableTicketOnlyForItsExactTaskArtifactAndChecksum() {
        var service = new ArtifactPreviewTicketService(
            Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(2), () -> "ticket-01"
        );
        var issued = service.issue(101L, OWNER, "STANDARD_COG", "sha256:abc");

        assertThat(service.validate(101L, "STANDARD_COG", "sha256:abc", issued.token())).isEqualTo(OWNER);
        assertThat(service.validate(101L, "STANDARD_COG", "sha256:abc", issued.token())).isEqualTo(OWNER);
        assertThat(service.resolve(101L, "STANDARD_COG", issued.token()).checksum()).isEqualTo("sha256:abc");
        assertThatThrownBy(() -> service.validate(101L, "MASK_COG", "sha256:abc", issued.token()))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.validate(101L, "STANDARD_COG", "sha256:def", issued.token()))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsAnExpiredTicket() {
        var now = new AtomicReference<>(Instant.parse("2026-09-11T00:00:00Z"));
        var service = new ArtifactPreviewTicketService(
            Clock.tick(new ReferenceClock(now), Duration.ofMillis(1)), Duration.ofMinutes(2), () -> "ticket-02"
        );
        var issued = service.issue(101L, OWNER, "STANDARD_COG", "sha256:abc");
        now.set(Instant.parse("2026-09-11T00:03:00Z"));

        assertThatThrownBy(() -> service.validate(101L, "STANDARD_COG", "sha256:abc", issued.token()))
            .isInstanceOf(SecurityException.class)
            .hasMessage("Artifact preview ticket is invalid or expired.");
    }

    private static final class ReferenceClock extends Clock {
        private final AtomicReference<Instant> now;

        private ReferenceClock(AtomicReference<Instant> now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
