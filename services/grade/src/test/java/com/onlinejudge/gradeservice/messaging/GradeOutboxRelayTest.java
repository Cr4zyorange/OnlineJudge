package com.onlinejudge.gradeservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "grade.rabbit.enabled=false")
class GradeOutboxRelayTest {
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM grade_event_outbox");
    }

    @Test
    void brokerFailureKeepsTheFactPendingAndRecoveryProducesOneConfirmedDelivery() {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO grade_event_outbox
                    (event_id,idempotency_key,event_type,payload_version,aggregate_type,aggregate_id,
                     aggregate_version,occurred_at,correlation_id,payload_json,delivery_status,
                     delivery_attempt,next_attempt_at,created_at)
                VALUES ('event-1','publish:1','grade.published.v2',2,'grade-publication','1',1,?,
                        'correlation-1','{}','PENDING',0,?,?)
                """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        GradeOutboxRepository repository = new GradeOutboxRepository(jdbc);
        AtomicBoolean brokerAvailable = new AtomicBoolean(false);
        GradeOutboxRelay relay = new GradeOutboxRelay(repository, event -> {
            if (!brokerAvailable.get()) throw new IllegalStateException("broker unavailable");
        });

        relay.publishPending();
        var failed = jdbc.queryForMap("SELECT delivery_status,delivery_attempt FROM grade_event_outbox WHERE event_id='event-1'");
        assertThat(failed.get("delivery_status")).isEqualTo("PENDING");
        assertThat(((Number) failed.get("delivery_attempt")).intValue()).isEqualTo(1);

        jdbc.update("UPDATE grade_event_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE event_id='event-1'");
        brokerAvailable.set(true);
        relay.publishPending();
        var delivered = jdbc.queryForMap("SELECT delivery_status,delivery_attempt,delivered_at FROM grade_event_outbox WHERE event_id='event-1'");
        assertThat(delivered.get("delivery_status")).isEqualTo("DELIVERED");
        assertThat(((Number) delivered.get("delivery_attempt")).intValue()).isEqualTo(2);
        assertThat(delivered.get("delivered_at")).isNotNull();
    }
}
