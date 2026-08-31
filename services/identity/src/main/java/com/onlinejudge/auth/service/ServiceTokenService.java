package com.onlinejudge.auth.service;

import com.onlinejudge.auth.exception.ServiceTokenException;
import com.onlinejudge.auth.repository.ServiceTokenIdempotencyRepository;
import com.onlinejudge.auth.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Issues one audience-bound service JWT per idempotent mTLS workload request. */
@Service
public class ServiceTokenService {
    private static final Set<String> VALID_AUDIENCES = Set.of("course", "assessment", "grade");

    private final JwtTokenService jwtTokenService;
    private final ServiceTokenIdempotencyRepository idempotency;
    private final Duration ttl;

    public ServiceTokenService(
            JwtTokenService jwtTokenService,
            ServiceTokenIdempotencyRepository idempotency,
            @Value("${onlinejudge.identity.service-tokens.ttl:PT5M}") Duration ttl
    ) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalStateException("Identity service-token TTL must be between 1 second and 5 minutes");
        }
        this.jwtTokenService = jwtTokenService;
        this.idempotency = idempotency;
        this.ttl = ttl;
    }

    /**
     * A replay record is a single-row uniqueness race, not a request-wide
     * transaction.  In particular, keep the retry read out of an InnoDB
     * REPEATABLE-READ snapshot: after a duplicate key, it must use a fresh
     * current read of the winning replica's committed row.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public JwtTokenService.IssuedServiceToken mint(
            String workloadSubject,
            String audience,
            List<String> scopes,
            String idempotencyKey
    ) {
        validate(audience, scopes, idempotencyKey);
        String fingerprint = fingerprint(audience, scopes);
        Instant now = Instant.now();
        idempotency.deleteExpired(now);
        Optional<JwtTokenService.IssuedServiceToken> existing = existing(workloadSubject, idempotencyKey, fingerprint, now);
        if (existing.isPresent()) {
            return existing.get();
        }

        JwtTokenService.IssuedServiceToken minted = jwtTokenService.issueServiceToken(workloadSubject, audience, scopes, ttl);
        if (idempotency.insert(workloadSubject, idempotencyKey, fingerprint, minted, now)) {
            return minted;
        }
        // A second replica may have won the unique key between our read and
        // insert.  Return that exact response, or reject a changed request.
        return existingCurrent(workloadSubject, idempotencyKey, fingerprint, Instant.now())
                .orElseThrow(() -> new IllegalStateException("service-token idempotency record disappeared"));
    }

    private void validate(String audience, List<String> scopes, String idempotencyKey) {
        if (audience == null || !VALID_AUDIENCES.contains(audience)) {
            throw ServiceTokenException.badRequest("audience must be one of course, assessment, grade");
        }
        if (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(scope -> scope == null || scope.isBlank())) {
            throw ServiceTokenException.badRequest("scopes must contain at least one non-blank value");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw ServiceTokenException.badRequest("Idempotency-Key length must be between 16 and 128");
        }
    }

    private Optional<JwtTokenService.IssuedServiceToken> existing(
            String workloadSubject, String idempotencyKey, String fingerprint, Instant now
    ) {
        return idempotency.findActive(workloadSubject, idempotencyKey, now).map(stored -> {
            if (!stored.fingerprint().equals(fingerprint)) {
                throw ServiceTokenException.idempotencyConflict();
            }
            return stored.token();
        });
    }

    private Optional<JwtTokenService.IssuedServiceToken> existingCurrent(
            String workloadSubject, String idempotencyKey, String fingerprint, Instant now
    ) {
        return idempotency.findActiveCurrent(workloadSubject, idempotencyKey, now).map(stored -> {
            if (!stored.fingerprint().equals(fingerprint)) {
                throw ServiceTokenException.idempotencyConflict();
            }
            return stored.token();
        });
    }

    private String fingerprint(String audience, List<String> scopes) {
        try {
            String canonical = audience + "\u0000" + String.join("\u0000", scopes.stream().sorted().toList());
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
