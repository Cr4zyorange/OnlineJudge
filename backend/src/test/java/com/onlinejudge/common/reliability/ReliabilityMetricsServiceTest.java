package com.onlinejudge.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.hwk.repository.AssessmentEventOutboxRepository;
import com.onlinejudge.lrn.repository.LearningReliabilityRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:reliability_metrics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        AssessmentEventOutboxRepository.class,
        LearningReliabilityRepository.class,
        ReliabilityMetricsService.class,
        ReliabilityMetricsServiceTest.TestConfig.class
})
@Sql(scripts = "file:../database/migrations/20260830_01_create_reliable_event_storage.sql")
class ReliabilityMetricsServiceTest {
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private AssessmentEventOutboxRepository outbox;

    @org.springframework.beans.factory.annotation.Autowired
    private LearningReliabilityRepository learning;

    @org.springframework.beans.factory.annotation.Autowired
    private ReliabilityMetricsService metrics;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void snapshotCarriesBacklogFailureAndCorrelationFieldsNeededForAnOperatorToTraceAnEvent() {
        jdbcTemplate.update("DELETE FROM assessment_event_outbox");
        jdbcTemplate.update("DELETE FROM learning_event_dead_letter");
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        outbox.appendHomeworkPublished(new Homework(
                91L, 88L, null, "Reliable messaging homework", "", HomeworkType.TEXT, HomeworkStatus.PUBLISHED,
                100, now.plusDays(7), true, false, false, null, 501L, now, false,
                now, now, List.of(), List.of(), null
        ));
        String eventId = jdbcTemplate.queryForObject("SELECT event_id FROM assessment_event_outbox", String.class);
        String correlationId = jdbcTemplate.queryForObject("SELECT correlation_id FROM assessment_event_outbox", String.class);
        learning.deadLetter("learning", "dlq-event", "assessment.homework.published.v2", "dlq-correlation",
                "{}", "NON_RETRYABLE", "test poison", 1, Instant.now());

        ReliabilityMetricsSnapshot snapshot = metrics.snapshot();

        assertThat(snapshot.assessmentBacklog()).isEqualTo(1);
        assertThat(snapshot.assessmentPending()).isEqualTo(1);
        assertThat(snapshot.assessmentFailed()).isZero();
        assertThat(snapshot.oldestAssessmentEventId()).isEqualTo(eventId);
        assertThat(snapshot.oldestAssessmentCorrelationId()).isEqualTo(correlationId);
        assertThat(snapshot.oldestAssessmentAgeSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.learningDeadLetters()).isEqualTo(1);
        assertThat(snapshot.oldestLearningDeadLetterEventId()).isEqualTo("dlq-event");
        assertThat(snapshot.oldestLearningDeadLetterCorrelationId()).isEqualTo("dlq-correlation");
    }
}
