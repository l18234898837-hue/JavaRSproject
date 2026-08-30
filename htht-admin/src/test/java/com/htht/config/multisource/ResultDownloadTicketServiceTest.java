package com.htht.config.multisource;

import com.htht.multisource.domain.model.AccessSubject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ResultDownloadTicketServiceTest {

    @Test
    void ticketIsShortLivedBoundToTheTaskAndUsableOnlyOnce() {
        Instant now = Instant.parse("2026-08-27T10:00:00Z");
        AtomicInteger sequence = new AtomicInteger();
        ResultDownloadTicketService service = new ResultDownloadTicketService(
            Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(2),
            () -> "ticket-" + sequence.incrementAndGet());
        AccessSubject owner = new AccessSubject(7L);

        var issued = service.issue(101L, owner, "fy3h-mersi-result.zip");
        var grant = service.consume(101L, issued.token());

        assertThat(issued.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(2)));
        assertThat(grant.subject()).isEqualTo(owner);
        assertThat(grant.fileName()).isEqualTo("fy3h-mersi-result.zip");
        assertThatThrownBy(() -> service.consume(101L, issued.token()))
            .isInstanceOf(SecurityException.class);

        var wrongTaskTicket = service.issue(101L, owner, "fy3h-mersi-result.zip");
        assertThatThrownBy(() -> service.consume(102L, wrongTaskTicket.token()))
            .isInstanceOf(SecurityException.class);
    }
}
