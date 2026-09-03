package com.onlinejudge.gradeservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeJwksCacheTest {

    @Test
    void unknownKidFailsLocallyWithoutFetchingIdentityOnTheRequestPath() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identity.createContext("/.well-known/jwks.json", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"keys\":[]}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        identity.start();
        try {
            GradeIdentityProperties properties = new GradeIdentityProperties();
            properties.setJwksUri("http://127.0.0.1:" + identity.getAddress().getPort() + "/.well-known/jwks.json");
            properties.setRequestTimeout(Duration.ofSeconds(1));
            GradeJwksCache cache = new GradeJwksCache(new ObjectMapper(), properties);

            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"RS256\",\"kid\":\"not-in-bootstrap\"}".getBytes());
            assertThatThrownBy(() -> cache.verify("Bearer " + header + ".e30.signature"))
                    .isInstanceOf(GradeJwtVerifier.UnknownKid.class);
            assertThat(requests.get()).isZero();
        } finally {
            identity.stop(0);
        }
    }
}
