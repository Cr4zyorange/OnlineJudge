package com.onlinejudge.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Locally held key snapshot; request authentication must never synchronously call Identity. */
@Component
public class IdentityJwksCache {
    private final ObjectMapper objectMapper;
    private final String issuer;
    private final String audience;
    private final AtomicReference<OfflineJwtVerifier> verifier = new AtomicReference<>();

    public IdentityJwksCache(
            ObjectMapper objectMapper,
            @Value("${onlinejudge.identity.jwt.issuer:onlinejudge.identity.v2}") String issuer,
            @Value("${onlinejudge.identity.jwt.audience:onlinejudge.api}") String audience,
            @Value("${onlinejudge.identity.jwks.trust-bundle:}") String initialJwks
    ) {
        this.objectMapper = objectMapper;
        this.issuer = issuer;
        this.audience = audience;
        if (initialJwks != null && !initialJwks.isBlank()) {
            replace(initialJwks);
        }
    }

    public Optional<OfflineJwtVerifier.Principal> verify(String token, OfflineJwtVerifier.SecurityVersionLookup securityVersions) {
        OfflineJwtVerifier current = verifier.get();
        if (current == null) {
            return Optional.empty();
        }
        OfflineJwtVerifier.Verification result = current.verify(token, securityVersions);
        return result.accepted() ? Optional.of(result.principal()) : Optional.empty();
    }

    /** Called by the bounded JWKS refresh/event consumer; no request path invokes this method. */
    public void replace(String jwksJson) {
        try {
            Map<String, Object> jwks = objectMapper.readValue(jwksJson, new TypeReference<>() { });
            verifier.set(OfflineJwtVerifier.fromJwks(objectMapper, Clock.systemUTC(), issuer, audience, jwks));
        } catch (Exception exception) {
            throw new IllegalArgumentException("IDENTITY_JWKS_TRUST_BUNDLE must be a valid v2 RS256 JWKS document", exception);
        }
    }
}
