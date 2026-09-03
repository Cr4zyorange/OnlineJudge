package com.onlinejudge.gradeservice.security;

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

/** Keeps the last validated bundle so valid sessions survive an Identity outage. */
@Component
public class GradeJwksCache {
    private final ObjectMapper json;
    private final GradeIdentityProperties properties;
    private final HttpClient client;
    private volatile Map<String, PublicKey> keys = Map.of();

    public GradeJwksCache(ObjectMapper json, GradeIdentityProperties properties) {
        this.json = json; this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(properties.getRequestTimeout()).build();
    }

    @PostConstruct
    void bootstrap() {
        if (properties.getJwksTrustBundle() != null && !properties.getJwksTrustBundle().isBlank()) {
            keys = GradeJwtVerifier.parseJwks(json, properties.getJwksTrustBundle());
        }
    }

    GradeJwtVerifier.Claims verify(String authorization) {
        return GradeJwtVerifier.verify(json, keys, authorization, properties.getIssuer(), properties.getAudience());
    }

    @Scheduled(initialDelayString = "${grade.identity.refresh-initial-delay-ms:30000}",
            fixedDelayString = "${grade.identity.refresh-interval-ms:300000}")
    public void scheduledRefresh() {
        if (properties.isRefreshEnabled()) refresh();
    }

    synchronized boolean refresh() {
        if (properties.getJwksUri() == null || properties.getJwksUri().isBlank()) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getJwksUri()))
                    .timeout(properties.getRequestTimeout()).header("Accept", "application/json")
                    .header("X-Request-Id", UUID.randomUUID().toString()).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.headers().firstValue("Content-Type")
                    .map(value -> !value.toLowerCase().contains("application/json")).orElse(true)) return false;
            Map<String, PublicKey> refreshed = GradeJwtVerifier.parseJwks(json, response.body());
            keys = refreshed;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
