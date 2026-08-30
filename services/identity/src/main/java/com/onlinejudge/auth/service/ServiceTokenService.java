package com.onlinejudge.auth.service;

import com.onlinejudge.auth.exception.ServiceTokenException;
import com.onlinejudge.auth.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Issues one audience-bound service JWT per idempotent mTLS workload request. */
@Service
public class ServiceTokenService {
    private static final Set<String> VALID_AUDIENCES = Set.of("course", "assessment", "grade", "learning");

    private final JwtTokenService jwtTokenService;
    private final Duration ttl;
    private final Map<IdempotencyKey, IssuedRequest> issuedRequests = new ConcurrentHashMap<>();

    public ServiceTokenService(
            JwtTokenService jwtTokenService,
            @Value("${onlinejudge.identity.service-tokens.ttl:PT5M}") Duration ttl
    ) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalStateException("Identity service-token TTL must be between 1 second and 5 minutes");
        }
        this.jwtTokenService = jwtTokenService;
        this.ttl = ttl;
    }

    public JwtTokenService.IssuedServiceToken mint(
            String workloadSubject,
            String audience,
            List<String> scopes,
            String idempotencyKey
    ) {
        validate(audience, scopes, idempotencyKey);
        IdempotencyKey key = new IdempotencyKey(workloadSubject, idempotencyKey);
        String fingerprint = audience + "\n" + String.join("\n", scopes.stream().sorted().toList());
        Instant now = Instant.now();
        IssuedRequest issued = issuedRequests.compute(key, (ignored, existing) -> {
            if (existing != null && existing.expiresAt().isAfter(now)) {
                if (!existing.fingerprint().equals(fingerprint)) {
                    throw ServiceTokenException.idempotencyConflict();
                }
                return existing;
            }
            JwtTokenService.IssuedServiceToken token = jwtTokenService.issueServiceToken(workloadSubject, audience, scopes, ttl);
            return new IssuedRequest(fingerprint, token);
        });
        return issued.token();
    }

    private void validate(String audience, List<String> scopes, String idempotencyKey) {
        if (audience == null || !VALID_AUDIENCES.contains(audience)) {
            throw ServiceTokenException.badRequest("audience must be one of course, assessment, grade, learning");
        }
        if (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(scope -> scope == null || scope.isBlank())) {
            throw ServiceTokenException.badRequest("scopes must contain at least one non-blank value");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw ServiceTokenException.badRequest("Idempotency-Key length must be between 16 and 128");
        }
    }

    private record IdempotencyKey(String workloadSubject, String value) {
    }

    private record IssuedRequest(String fingerprint, JwtTokenService.IssuedServiceToken token) {
        private Instant expiresAt() {
            return token.expiresAt();
        }
    }
}
