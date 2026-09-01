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

@Component
public final class ResultDownloadTicketService {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(2);

    private final Clock clock;
    private final Duration ttl;
    private final Supplier<String> tokenSupplier;
    private final ConcurrentMap<String, StoredTicket> tickets = new ConcurrentHashMap<>();

    public ResultDownloadTicketService() {
        this(Clock.systemUTC(), DEFAULT_TTL, () -> UUID.randomUUID().toString());
    }

    ResultDownloadTicketService(Clock clock, Duration ttl, Supplier<String> tokenSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Download ticket TTL must be positive.");
        }
    }

    public IssuedTicket issue(long taskId, AccessSubject subject, String requestedFileName) {
        Objects.requireNonNull(subject, "subject");
        Instant now = clock.instant();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Instant expiresAt = now.plus(ttl);
        StoredTicket stored = new StoredTicket(taskId, subject, fileName(taskId, requestedFileName), expiresAt);
        for (int attempt = 0; attempt < 5; attempt++) {
            String token = tokenSupplier.get();
            if (token != null && !token.isBlank() && tickets.putIfAbsent(token, stored) == null) {
                return new IssuedTicket(token, expiresAt);
            }
        }
        throw new IllegalStateException("Unable to allocate a result download ticket.");
    }

    public DownloadGrant consume(long taskId, String token) {
        StoredTicket stored = token == null || token.isBlank() ? null : tickets.remove(token);
        if (stored == null || stored.taskId() != taskId || !stored.expiresAt().isAfter(clock.instant())) {
            throw new SecurityException("Result download ticket is invalid or expired.");
        }
        return new DownloadGrant(stored.subject(), stored.fileName());
    }

    private static String fileName(long taskId, String requested) {
        if (requested != null && requested.matches("(?i)[A-Za-z0-9][A-Za-z0-9._-]{0,180}\\.zip")) {
            return requested;
        }
        return "processing-result-" + taskId + ".zip";
    }

    public record IssuedTicket(String token, Instant expiresAt) {
    }

    public record DownloadGrant(AccessSubject subject, String fileName) {
    }

    private record StoredTicket(long taskId, AccessSubject subject, String fileName, Instant expiresAt) {
    }
}
