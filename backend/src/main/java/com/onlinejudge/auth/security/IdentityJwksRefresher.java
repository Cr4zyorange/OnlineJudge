package com.onlinejudge.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.onlinejudge.lrn.security.ServiceJwtVerifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Refreshes a previously provisioned trust bundle outside business requests. A failed refresh
 * deliberately leaves the last valid snapshot in place so an Identity outage does not evict
 * existing sessions from offline verification.
 */
@Component
public class IdentityJwksRefresher {
    private static final Logger log = LoggerFactory.getLogger(IdentityJwksRefresher.class);

    private final IdentityJwksCache cache;
    private final ServiceJwtVerifier serviceJwtVerifier;
    private final HttpClient client;
    private final URI endpoint;
    private final String requestId;
    private final Duration requestTimeout;

    @Autowired
    public IdentityJwksRefresher(
            IdentityJwksCache cache,
            ServiceJwtVerifier serviceJwtVerifier,
            @Value("${onlinejudge.identity.jwks.uri:}") String endpoint,
            @Value("${onlinejudge.identity.jwks.refresh-request-id:identity-jwks-refresh}") String requestId,
            @Value("${onlinejudge.identity.jwks.request-timeout:PT1S}") Duration requestTimeout
    ) {
        this(cache, serviceJwtVerifier, endpoint, requestId, requestTimeout, HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .build());
    }

    IdentityJwksRefresher(
            IdentityJwksCache cache,
            ServiceJwtVerifier serviceJwtVerifier,
            String endpoint,
            String requestId,
            Duration requestTimeout,
            HttpClient client
    ) {
        this.cache = cache;
        this.serviceJwtVerifier = serviceJwtVerifier;
        this.client = client;
        this.endpoint = endpoint == null || endpoint.isBlank() ? null : URI.create(endpoint);
        this.requestId = requestId == null || requestId.isBlank() ? "identity-jwks-refresh" : requestId;
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Identity JWKS refresh timeout must be positive");
        }
        this.requestTimeout = requestTimeout;
    }

    @Scheduled(
            initialDelayString = "${onlinejudge.identity.jwks.refresh-initial-delay:PT30S}",
            fixedDelayString = "${onlinejudge.identity.jwks.refresh-interval:PT5M}"
    )
    public void refresh() {
        if (endpoint == null) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .header("X-Request-Id", requestId)
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Identity JWKS refresh returned status {}", response.statusCode());
                return;
            }
            cache.replace(response.body());
            if (serviceJwtVerifier != null) {
                serviceJwtVerifier.replace(response.body());
            }
        } catch (Exception exception) {
            log.warn("Identity JWKS refresh failed; retaining last valid trust bundle: {}", exception.getClass().getSimpleName());
        }
    }
}
