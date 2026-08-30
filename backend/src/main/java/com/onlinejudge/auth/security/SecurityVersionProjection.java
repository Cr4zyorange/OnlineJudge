package com.onlinejudge.auth.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/** Local projection populated from identity.security-version.changed.v2 events. */
@Component
public class SecurityVersionProjection implements OfflineJwtVerifier.SecurityVersionLookup {
    private final ConcurrentHashMap<String, Long> minimumVersions = new ConcurrentHashMap<>();

    @Override
    public long minimumAcceptedVersion(String userId) {
        return minimumVersions.getOrDefault(userId, 0L);
    }

    public void apply(String userId, long securityVersion) {
        if (userId == null || userId.isBlank() || securityVersion < 0) {
            throw new IllegalArgumentException("Identity security-version event is invalid");
        }
        minimumVersions.merge(userId, securityVersion, Math::max);
    }
}
