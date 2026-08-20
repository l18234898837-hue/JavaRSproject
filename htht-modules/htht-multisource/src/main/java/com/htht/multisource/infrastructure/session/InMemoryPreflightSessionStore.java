package com.htht.multisource.infrastructure.session;

import com.htht.multisource.application.port.out.PreflightSessionStore;
import com.htht.multisource.application.port.out.model.PreflightSession;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProductCandidate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Single-process preflight storage. Outstanding sessions are lost on service restart.
 */
public final class InMemoryPreflightSessionStore implements PreflightSessionStore {

    private final Clock clock;
    private final Duration ttl;
    private final Supplier<String> tokenGenerator;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public InMemoryPreflightSessionStore(Clock clock, Duration ttl, Supplier<String> tokenGenerator) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("Preflight session TTL must not be negative.");
        }
    }

    @Override
    public PreflightSession open(AccessSubject owner, List<InputAsset> inputs, List<ProductCandidate> candidates) {
        Objects.requireNonNull(owner, "owner");
        List<ProductCandidate> immutableCandidates = List.copyOf(candidates);
        Map<String, ProductCandidate> candidateById = new LinkedHashMap<>();
        for (int index = 0; index < immutableCandidates.size(); index++) {
            candidateById.put("c" + index, immutableCandidates.get(index));
        }

        String token = nextAvailableToken();
        PreflightSession session = new PreflightSession(
            token,
            clock.instant().plus(ttl),
            inputs,
            candidateById.entrySet().stream()
                .map(entry -> new PreflightSession.Candidate(entry.getKey(), entry.getValue()))
                .toList()
        );
        sessions.put(token, new SessionState(owner, session, candidateById));
        return session;
    }

    @Override
    public SelectionLease acquire(AccessSubject owner, String token, List<String> candidateIds) {
        Objects.requireNonNull(owner, "owner");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Preflight token must not be blank.");
        }
        List<String> requestedIds = List.copyOf(candidateIds);
        if (requestedIds.isEmpty()) {
            throw new IllegalArgumentException("At least one candidate id is required.");
        }

        SessionState state = sessions.get(token);
        if (state == null || state.session.expiresAt().compareTo(clock.instant()) <= 0) {
            throw new IllegalStateException("Preflight session is unavailable.");
        }
        if (!state.owner.equals(owner)) {
            throw new IllegalArgumentException("Preflight session does not belong to the current user.");
        }

        synchronized (state) {
            if (state.status != Status.PENDING) {
                throw new IllegalStateException("Preflight session is unavailable.");
            }
            List<ProductCandidate> selected = requestedIds.stream().map(state.candidates::get).toList();
            if (selected.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Preflight candidate is unavailable.");
            }
            state.status = Status.LEASED;
            return new Lease(state, selected);
        }
    }

    private String nextAvailableToken() {
        for (int attempts = 0; attempts < 10; attempts++) {
            String token = tokenGenerator.get();
            if (token != null && !token.isBlank() && !sessions.containsKey(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Unable to issue a unique preflight token.");
    }

    private enum Status {
        PENDING,
        LEASED,
        CONSUMED
    }

    private static final class SessionState {
        private final AccessSubject owner;
        private final PreflightSession session;
        private final Map<String, ProductCandidate> candidates;
        private Status status = Status.PENDING;

        private SessionState(AccessSubject owner, PreflightSession session, Map<String, ProductCandidate> candidates) {
            this.owner = owner;
            this.session = session;
            this.candidates = Map.copyOf(candidates);
        }
    }

    private static final class Lease implements SelectionLease {
        private final SessionState state;
        private final List<ProductCandidate> candidates;
        private boolean settled;

        private Lease(SessionState state, List<ProductCandidate> candidates) {
            this.state = state;
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public List<ProductCandidate> candidates() {
            return candidates;
        }

        @Override
        public void commit() {
            settle(Status.CONSUMED);
        }

        @Override
        public void release() {
            settle(Status.PENDING);
        }

        private void settle(Status status) {
            synchronized (state) {
                if (settled || state.status != Status.LEASED) {
                    throw new IllegalStateException("Preflight session lease is unavailable.");
                }
                state.status = status;
                settled = true;
            }
        }
    }
}
