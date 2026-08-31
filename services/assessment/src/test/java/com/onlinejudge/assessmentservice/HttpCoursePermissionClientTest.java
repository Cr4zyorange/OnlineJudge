package com.onlinejudge.assessmentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.assessmentservice.service.CourseAuthorizationUnavailableException;
import com.onlinejudge.assessmentservice.service.HttpCoursePermissionClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCoursePermissionClientTest {
    @Test
    void dependencyOutageIsDistinctFromExplicitAuthorizationDenial() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestId = new AtomicReference<>();
        try {
            server.createContext("/courses/course-1/permission/user-1", exchange -> {
                requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
                String body = "{\"allowed\":false,\"courseId\":\"course-1\",\"userId\":\"user-1\",\"action\":\"MANAGE\",\"memberVersion\":1}";
                exchange.sendResponseHeaders(200, body.getBytes().length);
                exchange.getResponseBody().write(body.getBytes());
                exchange.close();
            });
            server.createContext("/forbidden/course-1/user-1", exchange -> {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
            });
            server.start();
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/courses/{courseId}/permission/{userId}";
            HttpCoursePermissionClient denial = new HttpCoursePermissionClient(new ObjectMapper(), base, "assessment-test", Duration.ofSeconds(1));
            assertThat(denial.canManageCourse("course-1", "user-1", "request-314")).isFalse();
            assertThat(requestId.get()).isEqualTo("request-314");

            HttpCoursePermissionClient invalidServiceIdentity = new HttpCoursePermissionClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/forbidden/{courseId}/{userId}",
                    "assessment-test", Duration.ofSeconds(1));
            assertThatThrownBy(() -> invalidServiceIdentity.canManageCourse("course-1", "user-1"))
                    .isInstanceOf(CourseAuthorizationUnavailableException.class);

            HttpCoursePermissionClient outage = new HttpCoursePermissionClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/unavailable/{courseId}/{userId}",
                    "assessment-test", Duration.ofMillis(100));
            assertThatThrownBy(() -> outage.canManageCourse("course-1", "user-1"))
                    .isInstanceOf(CourseAuthorizationUnavailableException.class);
        } finally {
            server.stop(0);
        }
    }
}
