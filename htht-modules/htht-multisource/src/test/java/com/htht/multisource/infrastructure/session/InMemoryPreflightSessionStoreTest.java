package com.htht.multisource.infrastructure.session;

import com.htht.multisource.application.port.out.PreflightSessionStore;
import com.htht.multisource.application.port.out.model.PreflightSession;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StorageUri;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@Tag("dev")
public class InMemoryPreflightSessionStoreTest {

    private final Instant now = Instant.parse("2026-08-20T12:00:00Z");
    private final AccessSubject owner = new AccessSubject(7L);

    @Test
    void opensAnOpaqueOwnerBoundSessionWithServerCapturedCandidates() {
        InMemoryPreflightSessionStore store = storeAt(now);

        PreflightSession session = store.open(owner, List.of(asset()), List.of(candidate("first"), candidate("second")));

        assertThat(session.token()).isEqualTo("token-1");
        assertThat(session.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(session.candidates()).extracting(PreflightSession.Candidate::id)
            .containsExactly("c0", "c1");
        assertThat(session.candidates()).extracting(PreflightSession.Candidate::candidate)
            .containsExactly(candidate("first"), candidate("second"));
        assertThatIllegalArgumentException().isThrownBy(
            () -> store.acquire(new AccessSubject(8L), session.token(), List.of("c0"))
        );
    }

    @Test
    void commitsOnlyOneAcquiredSelectionAndReleasesFailedCreationForRetry() {
        InMemoryPreflightSessionStore store = storeAt(now);
        PreflightSession session = store.open(owner, List.of(asset()), List.of(candidate("first")));

        PreflightSessionStore.SelectionLease retryableLease = store.acquire(owner, session.token(), List.of("c0"));
        retryableLease.release();

        PreflightSessionStore.SelectionLease committedLease = store.acquire(owner, session.token(), List.of("c0"));
        assertThat(committedLease.candidates()).containsExactly(candidate("first"));
        committedLease.commit();

        assertThatIllegalStateException().isThrownBy(
            () -> store.acquire(owner, session.token(), List.of("c0"))
        );
    }

    @Test
    void rejectsExpiredSessionsAndUnknownCandidateIds() {
        InMemoryPreflightSessionStore store = storeAt(now);
        PreflightSession session = store.open(owner, List.of(asset()), List.of(candidate("first")));
        InMemoryPreflightSessionStore expiredStore = new InMemoryPreflightSessionStore(
            Clock.fixed(now, ZoneOffset.UTC), Duration.ZERO, () -> "token-2"
        );
        PreflightSession expired = expiredStore.open(owner, List.of(asset()), List.of(candidate("first")));

        assertThatIllegalArgumentException().isThrownBy(
            () -> store.acquire(owner, session.token(), List.of("missing"))
        );
        assertThatIllegalStateException().isThrownBy(
            () -> expiredStore.acquire(owner, expired.token(), List.of("c0"))
        );
    }

    private InMemoryPreflightSessionStore storeAt(Instant instant) {
        return new InMemoryPreflightSessionStore(
            Clock.fixed(instant, ZoneOffset.UTC), Duration.ofMinutes(30), () -> "token-1"
        );
    }

    private InputAsset asset() {
        return new InputAsset(
            "MAIN", 0, new AssetReference("SYS_FILE", "42"), new StorageUri("rs://sys-file/42"), "sha256:test", true
        );
    }

    private ProductCandidate candidate(String packageId) {
        return new ProductCandidate(
            packageId,
            new ProductProfileKey("FY-3H-MERSI", "v1"),
            ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.SUPPORTED,
            List.of(asset()),
            0.91
        );
    }
}
