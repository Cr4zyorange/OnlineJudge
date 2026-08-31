package com.onlinejudge.assessmentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCoursePermissionClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void grantedWhenCourseDecisionAllowsManageWithMatchingFields() throws Exception {
        startServer(200, """
                {"allowed":true,"courseId":"course-1","userId":"user-1","action":"MANAGE","memberVersion":2}
                """);
        assertThat(client().canManageCourse("course-1", "user-1")).isTrue();
    }

    @Test
    void deniedWhenCourseDecisionExplicitlyRejects() throws Exception {
        startServer(200, """
                {"allowed":false,"courseId":"course-1","userId":"user-1","action":"MANAGE","memberVersion":2}
                """);
        assertThat(client().canManageCourse("course-1", "user-1")).isFalse();
    }

    @Test
    void deniedWhenDecisionFieldsDoNotMatchTheRequest() throws Exception {
        startServer(200, """
                {"allowed":true,"courseId":"other-course","userId":"user-1","action":"MANAGE","memberVersion":2}
                """);
        assertThat(client().canManageCourse("course-1", "user-1")).isFalse();
    }

    @Test
    void deniedWhenCourseDecisionCarriesAStaleMemberVersion() throws Exception {
        startServer(200, """
                {"allowed":true,"courseId":"course-1","userId":"user-1","action":"MANAGE","memberVersion":0}
                """);
        assertThat(client().canManageCourse("course-1", "user-1")).isFalse();
    }

    @Test
    void unavailableWhenCourseRespondsWithAServiceError() throws Exception {
        startServer(503, """
                {"code":"COURSE_BUSY","message":"decision unavailable","requestId":"r-1","retryable":true}
                """);
        assertThatThrownBy(() -> client().canManageCourse("course-1", "user-1"))
                .isInstanceOf(CourseAuthorizationUnavailableException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    void unavailableWhenCourseEndpointCannotBeReached() throws Exception {
        startServer(200, "{}");
        int unreachablePort = server.getAddress().getPort();
        server.stop(0);
        server = null;
        HttpCoursePermissionClient unreachable = new HttpCoursePermissionClient(mapper,
                "http://127.0.0.1:" + unreachablePort + "/internal/v2/courses/{courseId}/authorizations/{userId}",
                "Bearer service-token", Duration.ofSeconds(1));
        assertThatThrownBy(() -> unreachable.canManageCourse("course-1", "user-1"))
                .isInstanceOf(CourseAuthorizationUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void unavailableWhenTheEndpointIsNotConfigured() {
        HttpCoursePermissionClient unconfigured = new HttpCoursePermissionClient(mapper, "", "", Duration.ofSeconds(1));
        assertThatThrownBy(() -> unconfigured.canManageCourse("course-1", "user-1"))
                .isInstanceOf(CourseAuthorizationUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    private HttpCoursePermissionClient client() {
        return new HttpCoursePermissionClient(mapper,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/v2/courses/{courseId}/authorizations/{userId}",
                "Bearer service-token", Duration.ofSeconds(1));
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v2/courses", exchange -> respond(exchange, status, body));
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
