package com.onlinejudge.courseservice;

import com.onlinejudge.courseservice.persistence.CourseOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CourseServiceApplication.class)
class CourseOutboxLeaseTest {
    @Autowired private CourseOutboxRepository outbox;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM course_event_outbox");
    }

    @Test
    void twoRelayOwnersAtomicallyClaimOnceAndExpiredOwnerCannotAcknowledgeTheNewGeneration() throws Exception {
        outbox.append("course.member.changed.v2", "course-member", "700:701", 1,
                "747c571e-3638-4bbe-9d8e-132410c8a743",
                Map.of("courseId", "700", "userId", "701", "membershipStatus", "ACTIVE", "memberVersion", 1));
        Instant now = Instant.now();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<List<CourseOutboxRepository.OutboxRecord>> first = pool.submit(() -> claimAfterStart("relay-one", now, ready, start));
            Future<List<CourseOutboxRepository.OutboxRecord>> second = pool.submit(() -> claimAfterStart("relay-two", now, ready, start));
            ready.await();
            start.countDown();
            List<CourseOutboxRepository.OutboxRecord> allClaims = new java.util.ArrayList<>();
            allClaims.addAll(first.get());
            allClaims.addAll(second.get());

            assertThat(allClaims).hasSize(1);
            CourseOutboxRepository.OutboxRecord firstLease = allClaims.getFirst();
            jdbcTemplate.update("UPDATE course_event_outbox SET lease_until = ? WHERE id = ?", java.sql.Timestamp.from(now.minusSeconds(1)), firstLease.id());
            CourseOutboxRepository.OutboxRecord secondLease = outbox.claimDue("relay-recovery", now.plusSeconds(1), Duration.ofSeconds(30), 1).getFirst();
            assertThat(secondLease.leaseGeneration()).isEqualTo(firstLease.leaseGeneration() + 1);
            assertThat(outbox.markPublished(firstLease, now.plusSeconds(1))).isZero();
            assertThat(outbox.markFailedAttempt(firstLease, now.plusSeconds(1), "late relay", 8,
                    Duration.ofSeconds(5), Duration.ofSeconds(60))).isEqualTo(CourseOutboxRepository.DeliveryUpdate.STALE);
            assertThat(outbox.markFailedAttempt(secondLease, now.plusSeconds(1), "broker unavailable", 8,
                    Duration.ofSeconds(5), Duration.ofSeconds(60))).isEqualTo(CourseOutboxRepository.DeliveryUpdate.RETRY);
            Instant retryAt = jdbcTemplate.queryForObject("SELECT next_attempt_at FROM course_event_outbox WHERE id = ?",
                    (rs, row) -> rs.getTimestamp(1).toInstant(), secondLease.id());
            // MySQL's legacy DATETIME column rounds fractional seconds; the
            // five-second first backoff may therefore persist up to one second later.
            assertThat(retryAt).isBetween(now.plusSeconds(5), now.plusSeconds(7));
            assertThat(outbox.claimDue("too-early", retryAt.minusSeconds(1), Duration.ofSeconds(30), 1)).isEmpty();
            assertThat(outbox.claimDue("recovered-relay", retryAt.plusSeconds(1), Duration.ofSeconds(30), 1)).hasSize(1);
        }
    }

    @Test
    void terminalFailureRetainsAuditAndRequiresAnExplicitDurableRecovery() {
        outbox.append("course.member.changed.v2", "course-member", "702:703", 1,
                "c0690c72-3e10-4323-8c61-7f115f8ce25d",
                Map.of("courseId", "702", "userId", "703", "membershipStatus", "ACTIVE", "memberVersion", 1));
        Instant now = Instant.now();
        CourseOutboxRepository.OutboxRecord claim = outbox.claimDue("terminal-relay", now, Duration.ofSeconds(30), 1).getFirst();

        assertThat(outbox.markFailedAttempt(claim, now, "permanent broker refusal", 1,
                Duration.ofSeconds(5), Duration.ofSeconds(60))).isEqualTo(CourseOutboxRepository.DeliveryUpdate.FAILED);
        assertThat(jdbcTemplate.queryForMap("SELECT delivery_status, attempt_count, last_error FROM course_event_outbox WHERE id = ?", claim.id()))
                .containsEntry("delivery_status", "FAILED").containsEntry("attempt_count", 1)
                .containsEntry("last_error", "permanent broker refusal");
        assertThat(outbox.recoverFailed(claim.id(), "operator-incident-312", now.minusSeconds(1))).isTrue();
        assertThat(outbox.claimDue("manual-recovery-relay", now, Duration.ofSeconds(30), 1)).hasSize(1);
    }

    private List<CourseOutboxRepository.OutboxRecord> claimAfterStart(String owner, Instant now, CountDownLatch ready,
                                                                         CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return outbox.claimDue(owner, now, Duration.ofSeconds(30), 1);
    }
}
