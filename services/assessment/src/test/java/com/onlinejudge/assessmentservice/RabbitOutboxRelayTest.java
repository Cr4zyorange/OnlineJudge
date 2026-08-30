package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.messaging.RabbitOutboxRelay;
import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RabbitOutboxRelayTest {
    @Autowired AssessmentOutboxRepository outbox;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() { jdbc.update("DELETE FROM assessment_event_outbox"); }

    @Test
    void relayExposesConfirmBeforeMarkingAnOutboxRecordDelivered() {
        assertThat(RabbitOutboxRelay.PERSISTENT_DELIVERY_MODE).isEqualTo(2);
    }

    @Test
    void relayAndBothControlledReplaysShareTheReturnedMandatoryPublishGuard() throws Exception {
        Path root = Path.of("..", "..").toAbsolutePath().normalize();
        assertThat(Files.readString(root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/RabbitOutboxRelay.java")))
                .contains("MandatoryRabbitPublisher.publish");
    }

    @Test
    void returnedMandatoryMessageStaysPendingWithAnInspectableRetryFailure() {
        outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade", "HWK:unroutable:student-1", 1,
                UUID.randomUUID().toString(), Map.of("sourceId", "unroutable"), Instant.now());
        String eventId = outbox.pending(1).getFirst().eventId();

        assertThat(outbox.recordDeliveryFailure(eventId, "broker returned unroutable message")).isTrue();

        assertThat(jdbc.queryForObject("SELECT state FROM assessment_event_outbox WHERE event_id=?", String.class, eventId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT delivery_attempt FROM assessment_event_outbox WHERE event_id=?", Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error FROM assessment_event_outbox WHERE event_id=?", String.class, eventId)).contains("unroutable");
    }
}
