package com.onlinejudge.assessmentservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.util.Map;
import java.util.UUID;

@Component
public class JwksCache {
    private final ObjectMapper mapper; private final AssessmentIdentityProperties properties; private final HttpClient http;
    private volatile Map<String, PublicKey> keys = Map.of();
    public JwksCache(ObjectMapper mapper, AssessmentIdentityProperties properties) { this.mapper = mapper; this.properties = properties; this.http = HttpClient.newBuilder().connectTimeout(properties.getRequestTimeout()).build(); }
    @PostConstruct void bootstrap() { if (!properties.getJwksTrustBundle().isBlank()) keys = JwtVerifier.parseJwks(mapper, properties.getJwksTrustBundle()); }
    public JwtVerifier.Claims verifyUser(String header) { return verify(header, properties.getAudience()); }
    public JwtVerifier.Claims verify(String header, String audience) { try { return JwtVerifier.verify(mapper, keys, header, properties.getIssuer(), audience); } catch (JwtVerifier.UnknownKid e) { refreshOnce(); return JwtVerifier.verify(mapper, keys, header, properties.getIssuer(), audience); } }
    @Scheduled(fixedDelayString = "${assessment.identity.refresh-interval:300000}") public void scheduledRefresh() { if (properties.isRefreshEnabled()) refreshOnce(); }
    /**
     * The identity JWKS endpoint is a traced contract endpoint, not a public static file.
     * Keep this operation bounded and retain the last verified key set when either the
     * endpoint or an intermediary violates the JSON/cache contract.
     */
    public synchronized boolean refreshOnce() {
        if (properties.getJwksUri().isBlank()) return false;
        try {
            var request = HttpRequest.newBuilder(URI.create(properties.getJwksUri()))
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .GET()
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !isCanonicalJwksResponse(response)) return false;
            // Parse before publishing the replacement so a malformed refresh cannot evict
            // prior/current keys that still validate an existing session during Identity loss.
            Map<String, PublicKey> refreshed = JwtVerifier.parseJwks(mapper, response.body());
            keys = refreshed;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isCanonicalJwksResponse(HttpResponse<String> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT);
        String cacheControl = response.headers().firstValue("Cache-Control").orElse("").toLowerCase(java.util.Locale.ROOT);
        return contentType.startsWith("application/json") && cacheControl.contains("max-age=") && cacheControl.contains("must-revalidate");
    }
}
