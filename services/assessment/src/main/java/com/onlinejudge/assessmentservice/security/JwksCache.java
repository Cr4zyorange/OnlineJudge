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

@Component
public class JwksCache {
    private final ObjectMapper mapper; private final AssessmentIdentityProperties properties; private final HttpClient http;
    private volatile Map<String, PublicKey> keys = Map.of();
    public JwksCache(ObjectMapper mapper, AssessmentIdentityProperties properties) { this.mapper = mapper; this.properties = properties; this.http = HttpClient.newBuilder().connectTimeout(properties.getRequestTimeout()).build(); }
    @PostConstruct void bootstrap() { if (!properties.getJwksTrustBundle().isBlank()) keys = JwtVerifier.parseJwks(mapper, properties.getJwksTrustBundle()); }
    public JwtVerifier.Claims verifyUser(String header) { return verify(header, properties.getAudience()); }
    public JwtVerifier.Claims verify(String header, String audience) { try { return JwtVerifier.verify(mapper, keys, header, properties.getIssuer(), audience); } catch (JwtVerifier.UnknownKid e) { refreshOnce(); return JwtVerifier.verify(mapper, keys, header, properties.getIssuer(), audience); } }
    @Scheduled(fixedDelayString = "${assessment.identity.refresh-interval:300000}") public void scheduledRefresh() { if (properties.isRefreshEnabled()) refreshOnce(); }
    public synchronized boolean refreshOnce() { if (properties.getJwksUri().isBlank()) return false; try { var request = HttpRequest.newBuilder(URI.create(properties.getJwksUri())).timeout(properties.getRequestTimeout()).GET().build(); var response = http.send(request, HttpResponse.BodyHandlers.ofString()); if (response.statusCode() != 200) return false; keys = JwtVerifier.parseJwks(mapper, response.body()); return true; } catch (Exception ignored) { return false; } }
}
