package com.onlinejudge.lrn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.EventProcessingDecision;
import com.onlinejudge.common.reliability.ConfirmedEventPublisher;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.lrn.repository.JdbcNotificationRepository;
import com.onlinejudge.lrn.repository.LearningCourseMemberProjectionRepository;
import com.onlinejudge.lrn.repository.LearningEventInboxRepository;
import com.onlinejudge.lrn.repository.LearningReliabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:learning_reliable_consumer;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        JdbcNotificationRepository.class,
        LearningCourseMemberProjectionRepository.class,
        LearningEventInboxRepository.class,
        LearningReliabilityRepository.class,
        LearningHomeworkPublishedHandler.class,
        LearningCourseMemberChangedHandler.class,
        LearningCourseMembershipSnapshotHandler.class,
        LearningReliableEventConsumer.class,
        LearningReconciliationWorker.class,
        LearningDeadLetterReplayService.class,
        LearningReliableEventConsumerTest.TestConfig.class
})
@Sql(scripts = {
        "file:../database/migrations/20260603_01_create_lrn_notification.sql",
        "file:../database/migrations/20260830_01_create_reliable_event_storage.sql",
        "file:../database/migrations/20260831_02_create_learning_membership_watermark.sql"
})
class LearningReliableEventConsumerTest {
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RecordingPublisher confirmedEventPublisher() {
            return new RecordingPublisher();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private LearningReliableEventConsumer consumer;

    @org.springframework.beans.factory.annotation.Autowired
    private LearningCourseMemberProjectionRepository courseMembers;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private LearningDeadLetterReplayService replayService;

    @org.springframework.beans.factory.annotation.Autowired
    private LearningReconciliationWorker reconciliationWorker;

    @org.springframework.beans.factory.annotation.Autowired
    private RecordingPublisher publisher;

    @org.springframework.beans.factory.annotation.Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM lrn_notification_status_log");
        jdbcTemplate.update("DELETE FROM lrn_notification");
        jdbcTemplate.update("DELETE FROM learning_event_inbox");
        jdbcTemplate.update("DELETE FROM learning_event_delivery_attempt");
        jdbcTemplate.update("DELETE FROM learning_event_dead_letter");
        jdbcTemplate.update("DELETE FROM learning_event_reconciliation_request");
        jdbcTemplate.update("DELETE FROM learning_deferred_event");
        jdbcTemplate.update("DELETE FROM learning_course_member_projection");
        jdbcTemplate.update("DELETE FROM learning_course_membership_watermark");
        assertThat(consumer.consume(courseMembershipSnapshotEvent("baseline-roster", 1,
                new MemberFact(42L, "ACTIVE", 1L)))).isEqualTo(EventProcessingDecision.ACK);
        publisher.publishedEventIds.clear();
    }

    @Test
    void duplicateRedeliveryTenTimesCreatesOneLearningNotificationEvenWhenAckWasLost() {
        ReliableEventEnvelope event = homeworkEvent("event-1", 1, "Java collections homework");

        for (int redelivery = 0; redelivery < 10; redelivery++) {
            assertThat(consumer.consume(event)).isEqualTo(EventProcessingDecision.ACK);
        }

        assertThat(count("lrn_notification")).isEqualTo(1);
        assertThat(countByEventId("learning_event_inbox", "event-1")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT idempotency_key FROM lrn_notification", String.class
        )).isEqualTo("homework:91:42");
    }

    @Test
    void aggregateVersionGapIsDurablyDeferredAndCreatesAuditableReconciliation() {
        assertThat(consumer.consume(homeworkEvent("event-gap", 2, "Java collections homework")))
                .isEqualTo(EventProcessingDecision.ACK);

        assertThat(count("lrn_notification")).isZero();
        assertThat(countByEventId("learning_event_inbox", "event-gap")).isZero();
        assertThat(count("learning_deferred_event")).isEqualTo(1);
        assertThat(count("learning_event_reconciliation_request")).isEqualTo(1);
    }

    @Test
    void reconciliationEventuallyAppliesDeferredV2AfterTheMissingVersionArrives() {
        assertThat(consumer.consume(homeworkEvent("event-v2", 2, "Java collections homework")))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(countByEventId("learning_event_inbox", "event-v2")).isZero();

        assertThat(consumer.consume(homeworkEvent("event-v1", 1, "Java collections homework")))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(reconciliationWorker.reconcileDue(Instant.parse("2030-01-01T00:00:00.123456789Z"))).isEqualTo(1);

        assertThat(countByEventId("learning_event_inbox", "event-v1")).isEqualTo(1);
        assertThat(countByEventId("learning_event_inbox", "event-v2")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT processing_status FROM learning_event_inbox WHERE event_id = 'event-v2'", String.class
        )).isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT request_status FROM learning_event_reconciliation_request WHERE triggering_event_id = 'event-v2'", String.class
        )).isEqualTo("RESOLVED");
    }

    @Test
    void reconciliationWorkerIsNotItsOwnScheduledAdapterSoExplicitReplaysCannotRaceATimer() throws Exception {
        assertThat(LearningReconciliationWorker.class
                .getDeclaredMethod("reconcileDueMessages")
                .isAnnotationPresent(Scheduled.class)).isFalse();
        assertThat(applicationContext.getBeansOfType(LearningReconciliationScheduler.class)).isEmpty();
    }

    @Test
    void courseMemberEventBuildsTheProjectionAndUnblocksADeferredHomeworkNotification() {
        clearCourseRoster();

        assertThat(consumer.consume(homeworkEvent("homework-before-members", 1, "Java collections homework")))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(count("lrn_notification")).isZero();
        assertThat(countByEventId("learning_event_inbox", "homework-before-members")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deferral_reason FROM learning_deferred_event", String.class
        )).isEqualTo("MEMBERSHIP_PROJECTION_PENDING");

        assertThat(consumer.consume(courseMemberEvent("course-member-42", "ACTIVE", 1)))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT member_version FROM learning_course_member_projection WHERE course_id = 88 AND user_id = 42", Long.class
        )).isEqualTo(1L);
        assertThat(consumer.consume(courseMembershipSnapshotEvent("course-roster-1", 1,
                new MemberFact(42L, "ACTIVE", 1L)))).isEqualTo(EventProcessingDecision.ACK);

        assertThat(reconciliationWorker.reconcileDue(Instant.parse("2030-01-01T00:00:00.123456789Z"))).isEqualTo(1);
        assertThat(count("lrn_notification")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT processing_status FROM learning_event_inbox WHERE event_id = 'homework-before-members'", String.class
        )).isEqualTo("APPLIED");
    }

    @Test
    void partialMemberProjectionMustNotAcknowledgeHomeworkBeforeTheFullCourseRosterIsKnown() {
        clearCourseRoster();

        assertThat(consumer.consume(courseMemberEvent("member-a", 42L, "ACTIVE", 1)))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(consumer.consume(homeworkEvent("homework-partial-roster", 1, "Java collections homework")))
                .isEqualTo(EventProcessingDecision.ACK);

        assertThat(countByEventId("learning_event_inbox", "homework-partial-roster")).isZero();
        assertThat(count("lrn_notification")).isZero();
        assertThat(count("learning_deferred_event")).isEqualTo(1);

        assertThat(consumer.consume(courseMembershipSnapshotEvent("course-roster-partial", 1,
                new MemberFact(42L, "ACTIVE", 1L),
                new MemberFact(43L, "ACTIVE", 1L)))).isEqualTo(EventProcessingDecision.ACK);
        assertThat(reconciliationWorker.reconcileDue(Instant.parse("2030-01-01T00:00:00.123456789Z"))).isEqualTo(1);
        assertThat(countByEventId("learning_event_inbox", "homework-partial-roster")).isEqualTo(1);
        assertThat(count("lrn_notification")).isEqualTo(2);
    }

    @Test
    void removedOnlyMemberProjectionMustNotAcknowledgeHomeworkAsAnAuthoritativeEmptyRoster() {
        clearCourseRoster();

        assertThat(consumer.consume(courseMemberEvent("removed-only", 42L, "REMOVED", 1)))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(consumer.consume(homeworkEvent("homework-removed-only", 1, "Java collections homework")))
                .isEqualTo(EventProcessingDecision.ACK);

        assertThat(countByEventId("learning_event_inbox", "homework-removed-only")).isZero();
        assertThat(count("lrn_notification")).isZero();
        assertThat(count("learning_deferred_event")).isEqualTo(1);

        assertThat(consumer.consume(courseMembershipSnapshotEvent("course-roster-empty", 1)))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(reconciliationWorker.reconcileDue(Instant.parse("2030-01-01T00:00:00.123456789Z"))).isEqualTo(1);
        assertThat(countByEventId("learning_event_inbox", "homework-removed-only")).isEqualTo(1);
        assertThat(count("lrn_notification")).isZero();
    }

    @Test
    void rosterSnapshotVersionGapAndOutOfOrderRecoveryApplyHomeworkOnlyAfterRequiredWatermarkArrives() {
        clearCourseRoster();

        assertThat(consumer.consume(courseMembershipSnapshotEvent("roster-v2", 2,
                new MemberFact(42L, "ACTIVE", 1L),
                new MemberFact(43L, "ACTIVE", 1L))))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(consumer.consume(homeworkEvent("homework-after-roster-gap", 1, "Java collections homework")))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(consumer.consume(courseMembershipSnapshotEvent("roster-v1", 1,
                new MemberFact(42L, "ACTIVE", 1L))))
                .isEqualTo(EventProcessingDecision.ACK);

        assertThat(reconciliationWorker.reconcileDue(Instant.parse("2030-01-01T00:00:00.123456789Z"))).isEqualTo(2);
        assertThat(countByEventId("learning_event_inbox", "homework-after-roster-gap")).isEqualTo(1);
        assertThat(count("lrn_notification")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = 88", Long.class
        )).isEqualTo(2L);
    }

    @Test
    void poisonPayloadIsDurablyRecordedForDlqAndDoesNotBlockTheNextHealthyEvent() {
        assertThat(consumer.consume(homeworkEvent("poison-event", 1, "")))
                .isEqualTo(EventProcessingDecision.DEAD_LETTER);
        assertThat(count("learning_event_dead_letter")).isEqualTo(1);

        assertThat(consumer.consume(homeworkEvent("healthy-event", 1, "Java collections homework")))
                .isEqualTo(EventProcessingDecision.ACK);
        assertThat(count("lrn_notification")).isEqualTo(1);
    }

    @Test
    void operatorReplayRepublishesTheExactAuditedEventAndMarksItOnlyAfterConfirm() {
        assertThat(consumer.consume(homeworkEvent("poison-event", 1, "")))
                .isEqualTo(EventProcessingDecision.DEAD_LETTER);

        assertThat(replayService.replay("poison-event", "reliability-operator")).isTrue();
        assertThat(publisher.publishedEventIds).containsExactly("poison-event");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT replayed_by FROM learning_event_dead_letter WHERE event_id = ?", String.class, "poison-event"
        )).isEqualTo("reliability-operator");
        assertThat(replayService.replay("poison-event", "reliability-operator")).isFalse();
    }

    private ReliableEventEnvelope homeworkEvent(String eventId, long aggregateVersion, String title) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return new ReliableEventEnvelope(
                    eventId,
                    "assessment.homework.published.v2",
                    2,
                    "assessment-homework",
                    "91",
                    aggregateVersion,
                    Instant.parse("2026-08-30T09:15:30Z"),
                    "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5b",
                    mapper.readTree("""
                            {"courseId":"88","homeworkId":"91","title":"%s","deadline":"2026-09-06T16:00:00Z","receiverScope":"COURSE_ACTIVE_STUDENTS","publishedAt":"2026-08-30T09:15:30Z"}
                            """.formatted(title))
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private ReliableEventEnvelope courseMemberEvent(String eventId, String membershipStatus, long memberVersion) {
        return courseMemberEvent(eventId, 42L, membershipStatus, memberVersion);
    }

    private ReliableEventEnvelope courseMemberEvent(String eventId, long userId, String membershipStatus, long memberVersion) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return new ReliableEventEnvelope(
                    eventId,
                    "course.member.changed.v2",
                    2,
                    "course-member",
                    "88:" + userId,
                    memberVersion,
                    Instant.parse("2026-08-30T09:15:30Z"),
                    "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5b",
                    mapper.readTree("""
                            {"courseId":"88","userId":"%d","membershipStatus":"%s","memberVersion":%d}
                            """.formatted(userId, membershipStatus, memberVersion))
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private ReliableEventEnvelope courseMembershipSnapshotEvent(String eventId, long rosterVersion, MemberFact... members) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String memberJson = java.util.Arrays.stream(members)
                    .map(member -> """
                            {"userId":"%d","membershipStatus":"%s","memberVersion":%d}
                            """.formatted(member.userId(), member.membershipStatus(), member.memberVersion()))
                    .collect(java.util.stream.Collectors.joining(","));
            return new ReliableEventEnvelope(
                    eventId,
                    "course.membership.snapshot.v2",
                    2,
                    "course-membership-roster",
                    "88",
                    rosterVersion,
                    Instant.parse("2026-08-30T09:15:30Z"),
                    "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5b",
                    mapper.readTree("""
                            {"courseId":"88","rosterVersion":%d,"members":[%s]}
                            """.formatted(rosterVersion, memberJson))
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int countByEventId(String table, String eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE event_id = ?", Integer.class, eventId);
    }

    private void clearCourseRoster() {
        jdbcTemplate.update("DELETE FROM learning_course_member_projection");
        jdbcTemplate.update("DELETE FROM learning_course_membership_watermark");
        jdbcTemplate.update("DELETE FROM learning_event_inbox WHERE aggregate_type = 'course-membership-roster'");
    }

    private record MemberFact(long userId, String membershipStatus, long memberVersion) {
    }

    static class RecordingPublisher implements ConfirmedEventPublisher {
        private final List<String> publishedEventIds = new ArrayList<>();

        @Override
        public void publish(ReliableEventEnvelope envelope, String routingKey) {
            publishedEventIds.add(envelope.eventId());
        }
    }
}
