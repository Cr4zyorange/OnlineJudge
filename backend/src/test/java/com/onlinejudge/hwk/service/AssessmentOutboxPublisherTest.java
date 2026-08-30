package com.onlinejudge.hwk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.BrokerUnavailableException;
import com.onlinejudge.common.reliability.ConfirmedEventPublisher;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.hwk.repository.AssessmentEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:assessment_outbox;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AssessmentEventOutboxRepository.class, AssessmentOutboxPublisherTest.TestConfig.class})
@Sql(scripts = "file:../database/migrations/20260830_01_create_reliable_event_storage.sql")
class AssessmentOutboxPublisherTest {
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private final RecordingConfirmedPublisher broker = new RecordingConfirmedPublisher();

    @org.springframework.beans.factory.annotation.Autowired
    private AssessmentEventOutboxRepository repository;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM assessment_event_outbox");
        broker.fail = false;
        broker.eventIds.clear();
    }

    @Test
    void brokerOutageKeepsCommittedEventForBoundedRetryThenConfirmedPublish() {
        repository.appendHomeworkPublished(publishedHomework());
        AssessmentOutboxPublisher outboxPublisher = publisher();
        Instant firstAttempt = Instant.parse("2026-08-30T10:00:00Z");
        broker.fail = true;

        assertThat(outboxPublisher.drain(firstAttempt)).isEqualTo(1);
        assertThat(status()).isEqualTo("RETRY");
        assertThat(attemptCount()).isEqualTo(1);
        assertThat(broker.eventIds).isEmpty();

        broker.fail = false;
        assertThat(outboxPublisher.drain(firstAttempt.plusSeconds(1))).isEqualTo(1);
        assertThat(status()).isEqualTo("PUBLISHED");
        assertThat(attemptCount()).isEqualTo(1);
        assertThat(broker.eventIds).hasSize(1);
    }

    @Test
    void repeatedDrainAfterConfirmDoesNotPublishEventAgain() {
        repository.appendHomeworkPublished(publishedHomework());
        AssessmentOutboxPublisher outboxPublisher = publisher();
        Instant now = Instant.parse("2026-08-30T10:00:00Z");

        assertThat(outboxPublisher.drain(now)).isEqualTo(1);
        assertThat(outboxPublisher.drain(now.plusSeconds(60))).isZero();
        assertThat(broker.eventIds).hasSize(1);
    }

    @Test
    void secondPublisherTakesOverAnExpiredLeaseAfterTheFirstPublisherCrashes() {
        repository.appendHomeworkPublished(publishedHomework());
        Instant claimedAt = Instant.parse("2026-08-30T10:00:00Z");
        long eventRowId = repository.claimDue("publisher-that-crashed", claimedAt, java.time.Duration.ofSeconds(30), 1)
                .getFirst().id();

        // The first process resumes only after its lease is no longer valid. It
        // must not be able to acknowledge a row that a second process may take.
        repository.markPublished(eventRowId, "publisher-that-crashed", claimedAt.plusSeconds(31));
        assertThat(status()).isEqualTo("IN_FLIGHT");

        AssessmentOutboxPublisher survivingPublisher = publisher();
        assertThat(survivingPublisher.drain(claimedAt.plusSeconds(31))).isEqualTo(1);
        assertThat(status()).isEqualTo("PUBLISHED");
        assertThat(broker.eventIds).hasSize(1);
    }

    private AssessmentOutboxPublisher publisher() {
        return new AssessmentOutboxPublisher(repository, broker, 10, 3, 30, 1, 16);
    }

    private Homework publishedHomework() {
        LocalDateTime publishedAt = LocalDateTime.ofInstant(Instant.parse("2026-08-30T10:00:00Z"), ZoneOffset.UTC);
        return new Homework(
                91L, 88L, null, "Java collections homework", "", HomeworkType.TEXT, HomeworkStatus.PUBLISHED,
                100, publishedAt.plusDays(7), true, false, false, null, 501L, publishedAt, false,
                publishedAt, publishedAt, List.of(), List.of(), null
        );
    }

    private String status() {
        return jdbcTemplate.queryForObject("SELECT delivery_status FROM assessment_event_outbox", String.class);
    }

    private int attemptCount() {
        return jdbcTemplate.queryForObject("SELECT attempt_count FROM assessment_event_outbox", Integer.class);
    }

    private static final class RecordingConfirmedPublisher implements ConfirmedEventPublisher {
        private final List<String> eventIds = new ArrayList<>();
        private boolean fail;

        @Override
        public void publish(com.onlinejudge.common.reliability.ReliableEventEnvelope envelope, String routingKey) {
            if (fail) {
                throw new BrokerUnavailableException("simulated RabbitMQ outage");
            }
            eventIds.add(envelope.eventId());
        }
    }
}
