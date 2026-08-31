package com.onlinejudge.gradeservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.gradeservice.messaging.SourceGradeChangedEnvelope;
import com.onlinejudge.gradeservice.service.SourceGradeProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SourceGradeProjectionServiceTest {
    @Autowired SourceGradeProjectionService projection;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM grade_source_reconciliation_request");
        jdbc.update("DELETE FROM grade_source_projection_gap");
        jdbc.update("DELETE FROM grade_source_deferred_event");
        jdbc.update("DELETE FROM grade_event_inbox");
        jdbc.update("DELETE FROM grade_source_projection");
        jdbc.update("DELETE FROM grade_source_projection_watermark");
    }

    @Test
    void duplicateAndStaleDeliveriesNeverOverwriteTheLatestSourceRevision() throws Exception {
        var version1 = event("11111111-1111-4111-8111-111111111111", 1, "SCORED", "72");
        var staleVersion1 = event("22222222-2222-4222-8222-222222222222", 1, "SCORED", "12");

        assertThat(projection.apply(version1).decision()).isEqualTo("APPLIED");
        assertThat(projection.apply(version1).decision()).isEqualTo("DUPLICATE");
        assertThat(projection.apply(staleVersion1).decision()).isEqualTo("STALE");

        assertThat(projectedScore()).isEqualByComparingTo("72");
        assertThat(projectedVersion()).isEqualTo(1L);
        assertThat(inboxCount()).isEqualTo(2);
    }

    @Test
    void aGapIsDurableAndTheProjectionAdvancesOnlyAfterTheMissingRevisionArrives() throws Exception {
        var version1 = event("11111111-1111-4111-8111-111111111111", 1, "SCORED", "72");
        var version3 = event("33333333-3333-4333-8333-333333333333", 3, "SCORED", "93");
        var version2 = event("22222222-2222-4222-8222-222222222222", 2, "SCORED", "82");

        assertThat(projection.apply(version1).decision()).isEqualTo("APPLIED");
        assertThat(projection.apply(version3).decision()).isEqualTo("GAP");
        assertThat(projectedVersion()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grade_source_deferred_event", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grade_source_reconciliation_request WHERE request_status='PENDING'", Integer.class)).isEqualTo(1);

        assertThat(projection.apply(version2).decision()).isEqualTo("APPLIED");
        assertThat(projectedScore()).isEqualByComparingTo("93");
        assertThat(projectedVersion()).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grade_source_deferred_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grade_source_projection_gap", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grade_source_reconciliation_request WHERE request_status='RESOLVED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void ungradedRemainsNullAndIsNotCollapsedIntoZeroOrMissing() throws Exception {
        var ungraded = event("11111111-1111-4111-8111-111111111111", 1, "UNGRADED", null);

        assertThat(projection.apply(ungraded).decision()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT source_status FROM grade_source_projection", String.class)).isEqualTo("UNGRADED");
        assertThat(jdbc.queryForObject("SELECT score FROM grade_source_projection", BigDecimal.class)).isNull();
    }

    @Test
    void concurrentRedeliveryKeepsOneAppliedInboxFactAndReportsAllOthersAsDuplicates() throws Exception {
        var sameEvent = event("11111111-1111-4111-8111-111111111111", 1, "SCORED", "72");
        int deliveryCount = 8;
        CountDownLatch ready = new CountDownLatch(deliveryCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(deliveryCount)) {
            var futures = java.util.stream.IntStream.range(0, deliveryCount)
                    .mapToObj(ignored -> pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        return projection.apply(sameEvent).decision();
                    }))
                    .toList();
            ready.await();
            start.countDown();
            List<String> decisions = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            }).toList();

            assertThat(decisions).containsOnly("APPLIED", "DUPLICATE");
            assertThat(decisions).filteredOn("APPLIED"::equals).hasSize(1);
        }
        assertThat(jdbc.queryForObject("SELECT processing_status FROM grade_event_inbox", String.class)).isEqualTo("APPLIED");
        assertThat(projectedVersion()).isEqualTo(1L);
    }

    private SourceGradeChangedEnvelope event(String eventId, long version, String status, String score) throws Exception {
        String scoreJson = score == null ? "null" : score;
        return SourceGradeChangedEnvelope.parse(json.readTree("""
                {"eventId":"%s","eventType":"assessment.source-grade.changed.v2","payloadVersion":2,
                 "aggregateType":"assessment-source-grade","aggregateId":"HWK:homework-91:student-42","aggregateVersion":%d,
                 "occurredAt":"2026-08-31T09:15:30Z","correlationId":"e2dc79b2-2c18-4dca-bc18-e8573e7d9fe5",
                 "payload":{"courseId":"course-88","sourceType":"HWK","sourceId":"homework-91","studentId":"student-42",
                 "score":%s,"fullScore":100,"status":"%s","sourceVersion":%d}}
                """.formatted(eventId, version, scoreJson, status, version)));
    }

    private BigDecimal projectedScore() {
        return jdbc.queryForObject("SELECT score FROM grade_source_projection WHERE aggregate_id='HWK:homework-91:student-42'", BigDecimal.class);
    }

    private long projectedVersion() {
        return jdbc.queryForObject("SELECT source_version FROM grade_source_projection WHERE aggregate_id='HWK:homework-91:student-42'", Long.class);
    }

    private int inboxCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM grade_event_inbox", Integer.class);
    }
}
