package com.onlinejudge.gradeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.gradeservice.messaging.SourceGradeChangedEnvelope;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "grade.rabbit.enabled=false")
class SourceGradeReconciliationWorkerTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SourceGradeProjectionService projection;
    @Autowired ObjectMapper json;
    private HttpServer server;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM grade_source_reconciliation_request");
        jdbc.update("DELETE FROM grade_source_projection_gap");
        jdbc.update("DELETE FROM grade_source_deferred_event");
        jdbc.update("DELETE FROM grade_event_inbox");
        jdbc.update("DELETE FROM grade_source_projection");
        jdbc.update("DELETE FROM grade_source_projection_watermark");
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void stableAssessmentSnapshotAuthoritativelyClosesAMissingRevisionGap() throws Exception {
        projection.apply(event(2));
        assertThat(count("grade_source_projection_gap")).isEqualTo(1);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v2/source-grades", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("courseId=41", "sourceType=LAB", "sourceId=71", "page=0", "size=100");
            assertThat(exchange.getRequestHeaders().getFirst("X-OnlineJudge-Service-Authorization")).isEqualTo("Bearer grade-test");
            byte[] body = """
                    {"items":[{"courseId":"41","sourceType":"LAB","sourceId":"71","studentId":"11","score":95,
                    "fullScore":100,"status":"SCORED","updatedAt":"2026-09-01T01:00:00Z","sourceVersion":2}],
                    "page":0,"size":100,"total":1,"sourceSnapshotVersion":12}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        SourceGradeReconciliationWorker worker = new SourceGradeReconciliationWorker(jdbc, projection, json,
                "http://127.0.0.1:" + server.getAddress().getPort(), "Bearer grade-test", Duration.ofSeconds(1));

        assertThat(worker.reconcilePending()).isEqualTo(1);
        assertThat(count("grade_source_projection_gap")).isZero();
        assertThat(jdbc.queryForObject("SELECT source_version FROM grade_source_projection WHERE aggregate_id='LAB:71:11'", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT request_status FROM grade_source_reconciliation_request WHERE aggregate_id='LAB:71:11'", String.class))
                .isEqualTo("RESOLVED");
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private static SourceGradeChangedEnvelope event(long version) {
        return new SourceGradeChangedEnvelope("94409d40-b115-4ad5-bb45-5410b82a24a4", "LAB:71:11", version,
                Instant.parse("2026-09-01T00:00:00Z"), "e2dc79b2-2c18-4dca-bc18-e8573e7d9fe5",
                "41", "LAB", "71", "11", new java.math.BigDecimal("95"), new java.math.BigDecimal("100"), "SCORED", version);
    }
}
