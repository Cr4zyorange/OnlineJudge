package com.onlinejudge.courseservice.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.config.CourseIdentityProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A deployable bootstrap bundle plus bounded refresh gives existing sessions an Identity-outage path. */
@Component
public class JwksCache {
    private static final Pattern CACHE_MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)(?:\\s*,|$)", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper objectMapper;
    private final CourseIdentityProperties properties;
    private final HttpClient client;
    private volatile Map<String, PublicKey> keys = Map.of();

    public JwksCache(ObjectMapper objectMapper, CourseIdentityProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(properties.getRequestTimeout()).build();
    }

    @PostConstruct
    void bootstrap() {
        if (properties.getJwksTrustBundle() != null && !properties.getJwksTrustBundle().isBlank()) {
            keys = JwtVerifier.parseJwks(objectMapper, properties.getJwksTrustBundle());
        }
    }

    public JwtVerifier.Claims verify(String token, String audience) {
        try {
            return JwtVerifier.verify(objectMapper, keys, token, properties.getIssuer(), audience);
        } catch (JwtVerifier.JwtRejectedException rejected) {
            if (rejected.reason() != JwtVerifier.Reason.UNKNOWN_KID) {
                throw rejected;
            }
            refreshOnce();
            return JwtVerifier.verify(objectMapper, keys, token, properties.getIssuer(), audience);
        }
    }

    @Scheduled(initialDelayString = "${course.identity.refresh-initial-delay:30000}", fixedDelayString = "${course.identity.refresh-interval:300000}")
    public void scheduledRefresh() {
        if (properties.isRefreshEnabled()) {
            refreshOnce();
        }
    }

    public synchronized boolean refreshOnce() {
        if (properties.getJwksUri() == null || properties.getJwksUri().isBlank()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getJwksUri()))
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !isJson(response) || !hasPositiveCacheMaxAge(response)) {
                return false;
            }
            // Parse the full canonical Identity bundle before replacing trusted keys; failed refreshes retain the old bundle.
            objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() { });
            keys = JwtVerifier.parseJwks(objectMapper, response.body());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isJson(HttpResponse<String> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase(java.util.Locale.ROOT).contains("application/json"))
                .orElse(false);
    }

    private boolean hasPositiveCacheMaxAge(HttpResponse<String> response) {
        String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
        Matcher matcher = CACHE_MAX_AGE.matcher(cacheControl);
        if (!matcher.find()) return false;
        try {
            return Long.parseLong(matcher.group(1)) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
