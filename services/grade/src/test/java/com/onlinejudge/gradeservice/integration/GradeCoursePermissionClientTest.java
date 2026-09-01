package com.onlinejudge.gradeservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeCoursePermissionClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesFrozenAuthorizationActionsAndRequiredServiceHeaders() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> serviceAuthorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v2/courses/41/authorizations/7", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            serviceAuthorization.set(exchange.getRequestHeaders().getFirst("X-OnlineJudge-Service-Authorization"));
            byte[] body = "{\"allowed\":true,\"courseId\":\"41\",\"userId\":\"7\",\"action\":\"MANAGE_GRADE\",\"memberVersion\":3}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        GradeCoursePermissionClient client = client(server.getAddress().getPort());

        assertThat(client.canManageCourseGrade(41, 7)).isTrue();
        assertThat(query.get()).isEqualTo("action=MANAGE_GRADE");
        assertThat(requestId.get()).isNotBlank();
        assertThat(serviceAuthorization.get()).isEqualTo("Bearer grade-service-test");
    }

    @Test
    void readsEveryMemberPageWithoutTreatingAnOutageAsAnEmptyCourse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v2/courses/41/members", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body = query.contains("page=0")
                    ? "{\"items\":[{\"userId\":\"11\",\"role\":\"STUDENT\",\"memberVersion\":1}],\"page\":0,\"size\":1,\"total\":2}"
                    : "{\"items\":[{\"userId\":\"12\",\"role\":\"STUDENT\",\"memberVersion\":2}],\"page\":1,\"size\":1,\"total\":2}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        GradeCoursePermissionClient client = new GradeCoursePermissionClient(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "Bearer grade-service-test", Duration.ofSeconds(1), 1);
        assertThat(client.listCourseStudentIds(41)).isEqualTo(List.of(11L, 12L));

        server.stop(0);
        server = null;
        assertThatThrownBy(() -> client.listCourseStudentIds(41))
                .isInstanceOf(CourseAuthorizationUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    private static GradeCoursePermissionClient client(int port) {
        return new GradeCoursePermissionClient(new ObjectMapper(), "http://127.0.0.1:" + port,
                "Bearer grade-service-test", Duration.ofSeconds(1), 100);
    }
}
