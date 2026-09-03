package com.onlinejudge.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.lrn.security.ServiceJwtVerifier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityJwksRefresherTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void backgroundRefreshUpdatesTheDeployableBundleAndOutageRetainsTheLastValidSnapshot() throws Exception {
        KeyPair initial = keyPair();
        KeyPair rotated = keyPair();
        String initialJwks = jwks(initial, "initial-kid");
        String rotatedJwks = jwks(rotated, "rotated-kid");
        IdentityJwksCache cache = new IdentityJwksCache(JSON, "onlinejudge.identity.v2", "onlinejudge.api", initialJwks);
        ServiceJwtVerifier serviceVerifier = new ServiceJwtVerifier(JSON, "onlinejudge.identity.v2", "course", initialJwks);
        AtomicReference<String> requestId = new AtomicReference<>();
        HttpServer identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identity.createContext("/.well-known/jwks.json", exchange -> {
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            byte[] response = rotatedJwks.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        identity.start();
        try {
            IdentityJwksRefresher refresher = new IdentityJwksRefresher(
                    cache,
                    serviceVerifier,
                    "http://127.0.0.1:" + identity.getAddress().getPort() + "/.well-known/jwks.json",
                    "refresh-test-request",
                    Duration.ofSeconds(1),
                    java.net.http.HttpClient.newHttpClient()
            );
            refresher.refresh();

            String existingToken = token(rotated, "rotated-kid");
            assertThat(requestId.get()).isEqualTo("refresh-test-request");
            assertThat(cache.verify(existingToken, userId -> 1)).isPresent();
            assertThat(serviceVerifier.verify("Bearer " + serviceToken(rotated, "rotated-kid")).scopes())
                    .contains("learning.tasks.read");

            identity.stop(0);
            refresher.refresh();
            assertThat(cache.verify(existingToken, userId -> 1)).isPresent();

            IdentityJwksCache restartedConsumer = new IdentityJwksCache(
                    JSON, "onlinejudge.identity.v2", "onlinejudge.api", rotatedJwks
            );
            assertThat(restartedConsumer.verify(existingToken, userId -> 1)).isPresent();
        } finally {
            identity.stop(0);
        }
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String jwks(KeyPair pair, String kid) throws Exception {
        RSAPublicKey key = (RSAPublicKey) pair.getPublic();
        return JSON.writeValueAsString(Map.of("keys", List.of(Map.of(
                "kty", "RSA", "use", "sig", "alg", "RS256", "kid", kid,
                "n", unsigned(key.getModulus()), "e", unsigned(key.getPublicExponent())
        ))));
    }

    private static String token(KeyPair pair, String kid) throws Exception {
        String header = encode(Map.of("alg", "RS256", "typ", "JWT", "kid", kid));
        Instant now = Instant.now();
        String payload = encode(Map.of(
                "userId", "cache-test-user", "roles", List.of("TEACHER"), "permissions", List.of(),
                "sessionId", "cache-test-session", "securityVersion", 1,
                "iat", now.getEpochSecond(), "exp", now.plusSeconds(300).getEpochSecond(),
                "iss", "onlinejudge.identity.v2", "aud", "onlinejudge.api"
        ));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String serviceToken(KeyPair pair, String kid) throws Exception {
        String header = encode(Map.of("alg", "RS256", "typ", "JWT", "kid", kid));
        Instant now = Instant.now();
        String payload = encode(Map.of(
                "sub", "course-service", "aud", "course", "scopes", List.of("learning.tasks.read"),
                "iat", now.getEpochSecond(), "exp", now.plusSeconds(300).getEpochSecond(),
                "iss", "onlinejudge.identity.v2"
        ));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String encode(Object value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(value));
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }
}
