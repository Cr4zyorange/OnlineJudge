package com.onlinejudge.assessmentservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwksCacheRefreshTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void unknownKidRefreshSendsUniqueRequestIdAndAcceptsTheIdentityCurrentAndPreviousKeySet() throws Exception {
        KeyPair previous = TestJwtFactory.rsaKeyPair();
        KeyPair current = TestJwtFactory.rsaKeyPair();
        String bootstrap = TestJwtFactory.jwks("previous-kid", previous);
        String rotated = JSON.writeValueAsString(Map.of("keys", List.of(
                JSON.readTree(TestJwtFactory.jwks("previous-kid", previous)).path("keys").get(0),
                JSON.readTree(TestJwtFactory.jwks("current-kid", current)).path("keys").get(0)
        )));
        List<String> requestIds = new ArrayList<>();
        HttpServer identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identity.createContext("/.well-known/jwks.json", exchange -> {
            String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
            if (requestId == null || requestId.isBlank()) {
                byte[] failure = "{\"code\":\"REQUEST_ID_REQUIRED\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, failure.length);
                exchange.getResponseBody().write(failure);
                exchange.close();
                return;
            }
            requestIds.add(requestId);
            byte[] response = rotated.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Cache-Control", "public, max-age=300, must-revalidate");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        identity.start();
        try {
            AssessmentIdentityProperties properties = new AssessmentIdentityProperties();
            properties.setJwksTrustBundle(bootstrap);
            properties.setJwksUri("http://127.0.0.1:" + identity.getAddress().getPort() + "/.well-known/jwks.json");
            properties.setRequestTimeout(Duration.ofSeconds(1));
            JwksCache cache = new JwksCache(JSON, properties);
            cache.bootstrap();

            assertThat(cache.verifyUser("Bearer " + TestJwtFactory.userToken(previous, "previous-kid", "previous-user", List.of("STUDENT"))).string("userId"))
                    .isEqualTo("previous-user");
            assertThat(cache.verifyUser("Bearer " + TestJwtFactory.userToken(current, "current-kid", "current-user", List.of("STUDENT"))).string("userId"))
                    .isEqualTo("current-user");
            assertThat(cache.refreshOnce()).isTrue();

            assertThat(requestIds).hasSize(2).allSatisfy(id -> assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
            assertThat(requestIds.get(0)).isNotEqualTo(requestIds.get(1));
        } finally {
            identity.stop(0);
        }
    }
}
