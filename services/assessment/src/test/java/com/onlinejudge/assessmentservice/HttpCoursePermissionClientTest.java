package com.onlinejudge.assessmentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.assessmentservice.service.CourseAuthorizationUnavailableException;
import com.onlinejudge.assessmentservice.service.HttpCoursePermissionClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCoursePermissionClientTest {
    @Test
    void dependencyOutageIsDistinctFromExplicitAuthorizationDenial() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/courses/course-1/permission/user-1", exchange -> {
                String body = "{\"allowed\":false,\"courseId\":\"course-1\",\"userId\":\"user-1\",\"action\":\"MANAGE\",\"memberVersion\":1}";
                exchange.sendResponseHeaders(200, body.getBytes().length);
                exchange.getResponseBody().write(body.getBytes());
                exchange.close();
            });
            server.start();
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/courses/{courseId}/permission/{userId}";
            HttpCoursePermissionClient denial = new HttpCoursePermissionClient(new ObjectMapper(), base, "assessment-test", Duration.ofSeconds(1));
            assertThat(denial.canManageCourse("course-1", "user-1")).isFalse();

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
