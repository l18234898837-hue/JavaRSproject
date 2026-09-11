package com.htht.config.multisource;

import com.htht.multisource.domain.model.AccessSubject;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Issues short-lived tickets for multiple tile requests from one registered raster artifact. */
@Component
public final class ArtifactPreviewTicketService {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(2);

    private final Clock clock;
    private final Duration ttl;
    private final Supplier<String> tokenSupplier;
    private final ConcurrentMap<String, StoredTicket> tickets = new ConcurrentHashMap<>();

    public ArtifactPreviewTicketService() {
        this(Clock.systemUTC(), DEFAULT_TTL, () -> UUID.randomUUID().toString());
    }

    ArtifactPreviewTicketService(Clock clock, Duration ttl, Supplier<String> tokenSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Artifact preview ticket TTL must be positive.");
        }
    }

    public IssuedTicket issue(long taskId, AccessSubject subject, String artifactType, String checksum) {
        Objects.requireNonNull(subject, "subject");
        requireArtifact(artifactType, checksum);
        Instant now = clock.instant();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        StoredTicket stored = new StoredTicket(taskId, subject, artifactType, checksum, now.plus(ttl));
        for (int attempt = 0; attempt < 5; attempt++) {
            String token = tokenSupplier.get();
            if (token != null && !token.isBlank() && tickets.putIfAbsent(token, stored) == null) {
                return new IssuedTicket(token, stored.expiresAt());
            }
        }
        throw new IllegalStateException("Unable to allocate an artifact preview ticket.");
    }

    public AccessSubject validate(long taskId, String artifactType, String checksum, String token) {
        requireArtifact(artifactType, checksum);
        PreviewGrant grant = resolve(taskId, artifactType, token);
        if (!grant.checksum().equals(checksum)) {
            throw new SecurityException("Artifact preview ticket is invalid or expired.");
        }
        return grant.subject();
    }

    /** Resolves the owner and registered checksum without requiring anonymous artifact lookup. */
    public PreviewGrant resolve(long taskId, String artifactType, String token) {
        if (artifactType == null || artifactType.isBlank()) {
            throw new IllegalArgumentException("Artifact preview ticket scope is invalid.");
        }
        StoredTicket stored = token == null || token.isBlank() ? null : tickets.get(token);
        if (stored == null || !stored.expiresAt().isAfter(clock.instant()) || stored.taskId() != taskId
            || !stored.artifactType().equals(artifactType)) {
            throw new SecurityException("Artifact preview ticket is invalid or expired.");
        }
        return new PreviewGrant(stored.subject(), stored.checksum());
    }

    private static void requireArtifact(String artifactType, String checksum) {
        if (artifactType == null || artifactType.isBlank() || checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("Artifact preview ticket scope is invalid.");
        }
    }

    public record IssuedTicket(String token, Instant expiresAt) {
    }

    public record PreviewGrant(AccessSubject subject, String checksum) {
    }

    private record StoredTicket(long taskId, AccessSubject subject, String artifactType, String checksum,
                                Instant expiresAt) {
    }
}
