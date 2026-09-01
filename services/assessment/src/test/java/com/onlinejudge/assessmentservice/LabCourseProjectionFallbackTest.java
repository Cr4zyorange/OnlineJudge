package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Issue #357: a partial Course projection must fail closed without writing LAB facts. */
@SpringBootTest(properties = "assessment.worker.enabled=false")
@AutoConfigureMockMvc
class LabCourseProjectionFallbackTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();
    private static final String COURSE_ID = "course-357";
    private static final String STUDENT_ID = "student-357";
    private static final String TIMEOUT_REQUEST_ID = "lab-357-timeout";
    private static final CountDownLatch delayedAuthorizationRequested = new CountDownLatch(1);
    private static final ExecutorService courseStubExecutor = Executors.newCachedThreadPool();
    private static final HttpServer courseStub = startCourseStub();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("lab-fallback-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
        registry.add("assessment.course.authorization-uri", () -> "http://127.0.0.1:" + courseStub.getAddress().getPort()
                + "/internal/v2/courses/{courseId}/authorizations/{userId}");
        registry.add("assessment.course.service-authorization", () -> "Bearer assessment-test");
        // Keep the client bound short so the delayed stub deterministically exercises the timeout path.
        registry.add("assessment.course.timeout", () -> "PT0.2S");
    }

    @AfterAll
    static void stopCourseStub() {
        courseStub.stop(0);
        courseStubExecutor.shutdownNow();
    }

    @BeforeEach
    void resetFacts() {
        jdbc.update("DELETE FROM evaluation_task");
        jdbc.update("DELETE FROM assessment_source_grade");
        jdbc.update("DELETE FROM assessment_lab_score");
        jdbc.update("DELETE FROM assessment_lab_submission");
        jdbc.update("DELETE FROM assessment_submission");
        jdbc.update("DELETE FROM assessment_course_projection_gap");
        jdbc.update("DELETE FROM assessment_course_member_projection");
        jdbc.update("DELETE FROM assessment_lab_experiment");
        jdbc.update("""
                INSERT INTO assessment_lab_experiment
                    (id, course_id, title, description, status, deadline, max_score, allowed_languages,
                     auto_evaluate, evaluation_mode, report_required, time_limit_ms, memory_limit_kb,
                     attachment_ids, deleted, created_by, created_at, updated_at)
                VALUES (35701, 'course-357', 'projection fallback', 'fallback test', 'PUBLISHED',
                        TIMESTAMP '2030-01-01 00:00:00', 100, 'python', TRUE, 'DOCKER_IO', FALSE,
                        30000, 262144, '', FALSE, 'teacher-357', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO assessment_course_member_projection
                    (course_id, user_id, membership_status, member_version)
                VALUES ('course-357', 'student-357', 'ACTIVE', 1)
                """);
        jdbc.update("""
                INSERT INTO assessment_course_projection_gap
                    (course_id, user_id, expected_version, observed_version)
                VALUES ('course-357', 'student-357', 1, 2)
                """);
    }

    @Test
    void courseAuthorizationIsUsedWhenProjectionHasAGap() throws Exception {
        mockMvc.perform(multipart("/api/v1/labs/35701/submissions")
                        .file("file", "print('ok')".getBytes())
                        .param("language", "python")
                        .header("Authorization", "Bearer " + token(STUDENT_ID, "STUDENT"))
                        .header("X-Request-Id", "lab-357-submit"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_submission", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isEqualTo(1);
    }

    @Test
    void delayedCourseAuthorizationTimesOutAndLeavesLabFactsUntouched() throws Exception {
        mockMvc.perform(multipart("/api/v1/labs/35701/submissions")
                        .file("file", "print('blocked')".getBytes())
                        .param("language", "python")
                        .header("Authorization", "Bearer " + token(STUDENT_ID, "STUDENT"))
                        .header("X-Request-Id", TIMEOUT_REQUEST_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COURSE_AUTHORIZATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value(TIMEOUT_REQUEST_ID))
                .andExpect(jsonPath("$.retryable").value(true));

        assertThat(delayedAuthorizationRequested.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_submission", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_submission", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_source_grade", Integer.class)).isZero();
    }

    private static HttpServer startCourseStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v2/courses", LabCourseProjectionFallbackTest::authorizeCourse);
            // Isolate delayed requests so one timeout scenario cannot starve the success scenario.
            server.setExecutor(courseStubExecutor);
            server.start();
            return server;
        } catch (IOException failure) {
            throw new IllegalStateException("could not start the Course authorization stub", failure);
        }
    }

    private static void authorizeCourse(HttpExchange exchange) throws IOException {
        if (TIMEOUT_REQUEST_ID.equals(exchange.getRequestHeaders().getFirst("X-Request-Id"))) {
            delayedAuthorizationRequested.countDown();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
            return;
        }
        byte[] response = ("{\"allowed\":true,\"courseId\":\"" + COURSE_ID
                + "\",\"userId\":\"" + STUDENT_ID + "\",\"action\":\"VIEW\",\"memberVersion\":2}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private String token(String userId, String role) {
        return TestJwtFactory.userToken(KEY, "lab-fallback-kid", userId, List.of(role));
    }
}
