package com.onlinejudge.courseservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onlinejudge.courseservice.config.CourseIdentityProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwksCacheTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeyPair previousKey = TestJwtFactory.rsaKeyPair();
    private final KeyPair currentKey = TestJwtFactory.rsaKeyPair();
    private final AtomicReference<String> jwks = new AtomicReference<>();
    private final AtomicReference<String> cacheControl = new AtomicReference<>("public, max-age=300, must-revalidate");
    private final List<String> requestIds = new ArrayList<>();
    private HttpServer identity;

    @BeforeEach
    void startIdentityJwksEndpoint() throws IOException {
        identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identity.createContext("/.well-known/jwks.json", this::serveJwks);
        identity.start();
    }

    @AfterEach
    void stopIdentityJwksEndpoint() {
        if (identity != null) identity.stop(0);
    }

    @Test
    void identityRotationRefreshSendsRequestIdAndAcceptsCurrentAndPreviousKeys() {
        jwks.set(combinedJwks());
        CourseIdentityProperties properties = new CourseIdentityProperties();
        properties.setJwksUri("http://127.0.0.1:" + identity.getAddress().getPort() + "/.well-known/jwks.json");
        properties.setJwksTrustBundle(TestJwtFactory.jwks("previous-kid", previousKey));
        JwksCache cache = new JwksCache(objectMapper, properties);
        cache.bootstrap();

        assertThat(cache.refreshOnce()).isTrue();
        assertThat(cache.verify(TestJwtFactory.userToken(previousKey, "previous-kid", "712", List.of("STUDENT"), List.of()), "onlinejudge.api")
                .string("userId")).isEqualTo("712");
        assertThat(cache.verify(TestJwtFactory.userToken(currentKey, "current-kid", "713", List.of("TEACHER"), List.of()), "onlinejudge.api")
                .string("userId")).isEqualTo("713");
        assertThat(requestIds).hasSize(1);
        assertThat(UUID.fromString(requestIds.getFirst())).isNotNull();
    }

    @Test
    void malformedOrUncacheableIdentityResponseRetainsTheExistingTrustBundle() {
        jwks.set("{not-json");
        cacheControl.set("public, max-age=0");
        CourseIdentityProperties properties = new CourseIdentityProperties();
        properties.setJwksUri("http://127.0.0.1:" + identity.getAddress().getPort() + "/.well-known/jwks.json");
        properties.setJwksTrustBundle(TestJwtFactory.jwks("previous-kid", previousKey));
        JwksCache cache = new JwksCache(objectMapper, properties);
        cache.bootstrap();

        assertThat(cache.refreshOnce()).isFalse();
        assertThat(cache.verify(TestJwtFactory.userToken(previousKey, "previous-kid", "714", List.of("STUDENT"), List.of()), "onlinejudge.api")
                .string("userId")).isEqualTo("714");
    }

    private void serveJwks(HttpExchange exchange) throws IOException {
        String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            write(exchange, 400, "{\"code\":\"REQUEST_ID_REQUIRED\"}", null);
            return;
        }
        requestIds.add(requestId);
        write(exchange, 200, jwks.get(), cacheControl.get());
    }

    private String combinedJwks() {
        try {
            ArrayNode keys = objectMapper.createArrayNode();
            keys.addAll((ArrayNode) objectMapper.readTree(TestJwtFactory.jwks("previous-kid", previousKey)).path("keys"));
            keys.addAll((ArrayNode) objectMapper.readTree(TestJwtFactory.jwks("current-kid", currentKey)).path("keys"));
            return objectMapper.writeValueAsString(((ObjectNode) objectMapper.createObjectNode()).set("keys", keys));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void write(HttpExchange exchange, int status, String body, String cacheControl) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (cacheControl != null) exchange.getResponseHeaders().set("Cache-Control", cacheControl);
        exchange.sendResponseHeaders(status, encoded.length);
        exchange.getResponseBody().write(encoded);
        exchange.close();
    }
}
