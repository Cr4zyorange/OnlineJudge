package com.onlinejudge.crs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.ConfirmedEventPublisher;
import com.onlinejudge.common.reliability.EventProcessingDecision;
import com.onlinejudge.common.reliability.RabbitMqConfirmedEventPublisher;
import com.onlinejudge.common.reliability.RabbitMqReliabilityConfiguration;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.CourseStatus;
import com.onlinejudge.crs.domain.EnrollmentMode;
import com.onlinejudge.crs.domain.dto.CourseCreateRequest;
import com.onlinejudge.crs.domain.dto.CourseJoinRequest;
import com.onlinejudge.crs.domain.dto.CourseResponse;
import com.onlinejudge.crs.mapper.CourseRepository;
import com.onlinejudge.crs.repository.CourseEventOutboxRepository;
import com.onlinejudge.lrn.repository.JdbcNotificationRepository;
import com.onlinejudge.lrn.repository.LearningCourseMemberProjectionRepository;
import com.onlinejudge.lrn.repository.LearningEventInboxRepository;
import com.onlinejudge.lrn.repository.LearningReliabilityRepository;
import com.onlinejudge.lrn.service.LearningCourseMemberChangedHandler;
import com.onlinejudge.lrn.service.LearningCourseMembershipSnapshotHandler;
import com.onlinejudge.lrn.service.LearningHomeworkPublishedHandler;
import com.onlinejudge.lrn.service.LearningReconciliationWorker;
import com.onlinejudge.lrn.service.LearningReliableEventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Disposable integration proof for the source-owned Course producer.  The
 * shell harness supplies an empty MySQL 8.4 and RabbitMQ 4.1; no shared
 * developer service is ever contacted.
 */
@EnabledIfEnvironmentVariable(named = "ONLINEJUDGE_LIVE_COURSE_ROSTER", matches = "true")
@SpringJUnitConfig(classes = CourseMembershipRabbitMySqlLiveTest.LiveConfiguration.class)
@TestPropertySource(properties = {
        "onlinejudge.reliability.rabbitmq.enabled=true",
        "onlinejudge.reliability.publisher.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "onlinejudge.course.schema-initializer.enabled=false",
        "onlinejudge.demo-data.enabled=false",
        "spring.sql.init.mode=never"
})
class CourseMembershipRabbitMySqlLiveTest {
    @Configuration
    @EnableAutoConfiguration
    @Import({
            CourseRepository.class,
            CourseEventOutboxRepository.class,
            CourseService.class,
            JdbcNotificationRepository.class,
            LearningCourseMemberProjectionRepository.class,
            LearningEventInboxRepository.class,
            LearningReliabilityRepository.class,
            LearningHomeworkPublishedHandler.class,
            LearningCourseMemberChangedHandler.class,
            LearningCourseMembershipSnapshotHandler.class,
            LearningReliableEventConsumer.class,
            LearningReconciliationWorker.class,
            RabbitMqReliabilityConfiguration.class,
            RabbitMqConfirmedEventPublisher.class
    })
    static class LiveConfiguration {
    }

    @DynamicPropertySource
    static void liveProperties(DynamicPropertyRegistry registry) {
        String mysqlHost = System.getProperty("oj.mysql.host", "127.0.0.1");
        String mysqlPort = System.getProperty("oj.mysql.port", "3306");
        String mysqlDatabase = System.getProperty("oj.mysql.database", "onlinejudge");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase
                // Exercise the same JDBC clock contract as the deployed
                // compose profile, rather than letting this disposable test
                // silently use a different MySQL timestamp interpretation.
                + "?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai"
                + "&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> System.getProperty("oj.mysql.username", "onlinejudge"));
        registry.add("spring.datasource.password", () -> System.getProperty("oj.mysql.password", ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.rabbitmq.host", () -> System.getProperty("oj.rabbit.host", "127.0.0.1"));
        registry.add("spring.rabbitmq.port", () -> Integer.parseInt(System.getProperty("oj.rabbit.port", "5672")));
        registry.add("spring.rabbitmq.username", () -> System.getProperty("oj.rabbit.username", "guest"));
        registry.add("spring.rabbitmq.password", () -> System.getProperty("oj.rabbit.password", "guest"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CourseService courses;

    @org.springframework.beans.factory.annotation.Autowired
    private CourseEventOutboxRepository outbox;

    @org.springframework.beans.factory.annotation.Autowired
    private ConfirmedEventPublisher publisher;

    @org.springframework.beans.factory.annotation.Autowired
    private LearningReliableEventConsumer learning;

    @org.springframework.beans.factory.annotation.Autowired
    private LearningReconciliationWorker reconciliation;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    @Qualifier("reliableRabbitTemplate")
    private RabbitTemplate rabbit;

    @BeforeEach
    void resetDisposableState() {
        jdbc.update("DELETE FROM lrn_notification_status_log");
        jdbc.update("DELETE FROM lrn_notification");
        jdbc.update("DELETE FROM learning_event_inbox");
        jdbc.update("DELETE FROM learning_event_delivery_attempt");
        jdbc.update("DELETE FROM learning_event_dead_letter");
        jdbc.update("DELETE FROM learning_event_reconciliation_request");
        jdbc.update("DELETE FROM learning_deferred_event");
        jdbc.update("DELETE FROM learning_course_member_projection");
        jdbc.update("DELETE FROM learning_course_membership_watermark");
        jdbc.update("DELETE FROM course_membership_reconciliation_checkpoint");
        jdbc.update("DELETE FROM course_event_outbox");
        jdbc.update("DELETE FROM crs_course_member");
        jdbc.update("DELETE FROM crs_course");
    }

    @Test
    void courseOutboxRoutesAnAtomicRosterThroughRabbitAndReleasesTheOriginalDeferredHomeworkOnce() throws Exception {
        long suffix = Math.abs(System.nanoTime());
        CurrentUser teacher = user(8_100_000L + suffix % 100_000L, "TEACHER");
        CurrentUser student = user(8_200_000L + suffix % 100_000L, "STUDENT");
        CourseResponse course = courses.create(new CourseCreateRequest(
                "live-course-roster-" + suffix, "", "2026-F", "SE", null,
                EnrollmentMode.PUBLIC, null, null, null, null, CourseStatus.ACTIVE
        ), teacher);
        ReliableEventEnvelope homework = homework(course.id(), "live-homework-" + suffix);

        assertThat(learning.consume(homework)).isEqualTo(EventProcessingDecision.ACK);
        assertThat(count("learning_deferred_event WHERE event_id = '" + homework.eventId() + "'"))
                .isEqualTo(1);

        courses.join(course.id(), new CourseJoinRequest(null, ""), student);
        assertThat(count("course_event_outbox WHERE delivery_status = 'PENDING' AND event_type = 'course.membership.snapshot.v2'"))
                .isEqualTo(2);
        assertThat(count("course_event_outbox WHERE delivery_status = 'PENDING' AND event_type = 'course.member.changed.v2'"))
                .isEqualTo(1);
        assertThat(count("course_event_outbox WHERE delivery_status = 'PENDING' "
                + "AND next_attempt_at <= DATE_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 SECOND)"))
                .as("committed Course outbox records are claimable on the publisher's next scheduled pass")
                .isEqualTo(3);

        List<ReliableEventEnvelope> delivered = publishAndReceive(3);
        for (ReliableEventEnvelope envelope : delivered) {
            assertThat(learning.consume(envelope)).isEqualTo(EventProcessingDecision.ACK);
        }
        assertThat(reconciliation.reconcileDue(Instant.parse("2030-01-01T00:00:00Z"))).isEqualTo(1);
        assertThat(count("lrn_notification WHERE user_id = " + student.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(2L);

        courses.removeMember(course.id(), student.id(), teacher);
        List<ReliableEventEnvelope> removal = publishAndReceive(2);
        for (ReliableEventEnvelope envelope : removal) {
            assertThat(learning.consume(envelope)).isEqualTo(EventProcessingDecision.ACK);
        }
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                "SELECT membership_status FROM learning_course_member_projection WHERE course_id = ? AND user_id = ?",
                String.class, course.id(), student.id())).isEqualTo(CourseMemberStatus.REMOVED.name());

        System.out.printf("course-roster-live courseId=%d homeworkEventId=%s homeworkCorrelationId=%s "
                        + "published=%d delivered=%d removalDelivered=%d watermark=3 notificationsForStudent=1%n",
                course.id(), homework.eventId(), homework.correlationId(), 5, delivered.size(), removal.size());
    }

    @Test
    void preexistingCourseWithoutHistoricalOutboxMustBootstrapItsRosterWithoutLearningCallingBack() throws Exception {
        long suffix = Math.abs(System.nanoTime());
        CurrentUser teacher = user(8_300_000L + suffix % 100_000L, "TEACHER");
        CurrentUser student = user(8_400_000L + suffix % 100_000L, "STUDENT");
        CourseResponse course = courses.create(new CourseCreateRequest(
                "preexisting-course-roster-" + suffix, "", "2026-F", "SE", null,
                EnrollmentMode.PUBLIC, null, null, null, null, CourseStatus.ACTIVE
        ), teacher);
        courses.join(course.id(), new CourseJoinRequest(null, ""), student);
        // Simulate a course and members committed before #337 was deployed:
        // business facts exist, but Course has never emitted a roster event.
        jdbc.update("DELETE FROM course_event_outbox WHERE aggregate_id = ? OR aggregate_id LIKE ?",
                String.valueOf(course.id()), course.id() + ":%");

        ReliableEventEnvelope homework = homework(course.id(), "preexisting-homework-" + suffix);
        assertThat(learning.consume(homework)).isEqualTo(EventProcessingDecision.ACK);
        assertThat(count("learning_deferred_event WHERE event_id = '" + homework.eventId() + "'"))
                .isEqualTo(1);
        assertThat(count("lrn_notification WHERE user_id = " + student.id())).isZero();

        // The source-owned scheduler scans Course state; no Learning callback
        // is involved. Its outbox row is the durable per-course checkpoint.
        CourseMembershipBootstrapper bootstrapper = new CourseMembershipBootstrapper(outbox, 10);
        assertThat(bootstrapper.bootstrapMissingRosters()).isEqualTo(1);
        assertThat(bootstrapper.bootstrapMissingRosters()).isZero();
        assertThat(count("course_event_outbox WHERE aggregate_type = 'course-membership-roster' "
                + "AND aggregate_id = '" + course.id() + "'"))
                .as("preexisting Course must create its durable bootstrap snapshot")
                .isEqualTo(1);

        List<ReliableEventEnvelope> delivered = publishAndReceive(1);
        assertThat(delivered).singleElement().satisfies(envelope -> {
            assertThat(envelope.eventType()).isEqualTo(CourseEventOutboxRepository.MEMBERSHIP_SNAPSHOT);
            assertThat(envelope.aggregateId()).isEqualTo(String.valueOf(course.id()));
        });
        assertThat(learning.consume(delivered.getFirst())).isEqualTo(EventProcessingDecision.ACK);
        assertThat(reconciliation.reconcileDue(Instant.parse("2030-01-01T00:00:00Z"))).isEqualTo(1);
        assertThat(count("lrn_notification WHERE user_id = " + student.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(1L);

        System.out.printf("course-roster-bootstrap-live courseId=%d eventId=%s correlationId=%s "
                        + "bootstrapped=1 repeated=0 delivered=1 watermark=1 notificationsForStudent=1%n",
                course.id(), delivered.getFirst().eventId(), delivered.getFirst().correlationId());
    }

    @Test
    void publishedCourseSnapshotMustBeReissuedWhenLearningRestoresAnEmptyProjection() throws Exception {
        long suffix = Math.abs(System.nanoTime());
        CurrentUser teacher = user(8_500_000L + suffix % 100_000L, "TEACHER");
        CurrentUser student = user(8_600_000L + suffix % 100_000L, "STUDENT");
        CourseResponse course = courses.create(new CourseCreateRequest(
                "restored-learning-projection-" + suffix, "", "2026-F", "SE", null,
                EnrollmentMode.PUBLIC, null, null, null, null, CourseStatus.ACTIVE
        ), teacher);
        courses.join(course.id(), new CourseJoinRequest(null, ""), student);

        // Course has already published its v1/v2 snapshots.  Simulate an
        // independent Learning projection restore after those facts are gone.
        for (ReliableEventEnvelope event : publishAndReceive(3)) {
            assertThat(learning.consume(event)).isEqualTo(EventProcessingDecision.ACK);
        }
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(2L);
        jdbc.update("DELETE FROM lrn_notification_status_log");
        jdbc.update("DELETE FROM lrn_notification");
        jdbc.update("DELETE FROM learning_event_inbox");
        jdbc.update("DELETE FROM learning_course_member_projection");
        jdbc.update("DELETE FROM learning_course_membership_watermark");

        ReliableEventEnvelope homework = homework(course.id(), "restored-projection-homework-" + suffix);
        assertThat(learning.consume(homework)).isEqualTo(EventProcessingDecision.ACK);
        assertThat(count("learning_deferred_event WHERE event_id = '" + homework.eventId() + "'"))
                .isEqualTo(1);
        assertThat(count("lrn_notification WHERE user_id = " + student.id())).isZero();
        assertThat(count("course_event_outbox WHERE aggregate_type = 'course-membership-roster' "
                + "AND aggregate_id = '" + course.id() + "' AND delivery_status = 'PUBLISHED'"))
                .isEqualTo(2);

        // RED: an old PUBLISHED snapshot is not a usable recovery trigger.
        // Course must own a durable reconciliation path that emits v3 without
        // Learning synchronously asking it to do so.
        CourseMembershipBootstrapper bootstrapper = new CourseMembershipBootstrapper(outbox, 10);
        assertThat(bootstrapper.reconcilePublishedRosters(Instant.parse("2030-01-01T00:00:00Z")))
                .as("Course reconciliation must reissue a snapshot after Learning projection restore")
                .isEqualTo(1);
        assertThat(bootstrapper.reconcilePublishedRosters(Instant.parse("2030-01-01T00:00:00Z")))
                .as("a durable checkpoint must suppress a repeated recovery trigger")
                .isZero();
        assertThat(count("course_event_outbox WHERE aggregate_type = 'course-membership-roster' "
                + "AND aggregate_id = '" + course.id() + "' AND aggregate_version = 3"))
                .as("source reconciliation must advance the Course roster aggregate version")
                .isEqualTo(1);

        List<ReliableEventEnvelope> recovered = publishAndReceive(1);
        assertThat(recovered).singleElement().satisfies(envelope -> {
            assertThat(envelope.eventType()).isEqualTo(CourseEventOutboxRepository.MEMBERSHIP_SNAPSHOT);
            assertThat(envelope.aggregateVersion()).isEqualTo(3L);
        });
        assertThat(learning.consume(recovered.getFirst())).isEqualTo(EventProcessingDecision.ACK);
        assertThat(reconciliation.reconcileDue(Instant.parse("2030-01-01T00:00:01Z"))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(3L);
        assertThat(count("lrn_notification WHERE user_id = " + student.id())).isEqualTo(1);

        System.out.printf("course-roster-reconciliation-live courseId=%d eventId=%s correlationId=%s "
                        + "oldSnapshotVersion=2 reconciledSnapshotVersion=3 reconciled=1 repeated=0 delivered=1 "
                        + "watermark=3 notificationsForStudent=1%n",
                course.id(), recovered.getFirst().eventId(), recovered.getFirst().correlationId());
    }

    private List<ReliableEventEnvelope> publishAndReceive(int expected) {
        CourseOutboxPublisher outboxPublisher = new CourseOutboxPublisher(outbox, publisher, 50, 3, 30, 1, 16);
        // MySQL TIMESTAMP has second precision in the checked-in schema, so a
        // just-committed nanosecond instant can be rounded into the following
        // second. The production publisher's five-second cadence already
        // covers this; use the next bounded pass in the disposable proof.
        assertThat(outboxPublisher.drain(Instant.now().plusSeconds(2))).isEqualTo(expected);
        assertThat(count("course_event_outbox WHERE delivery_status = 'PUBLISHED'")).isGreaterThanOrEqualTo(expected);
        List<ReliableEventEnvelope> events = new ArrayList<>();
        for (int index = 0; index < expected; index++) {
            Message message = rabbit.receive(RabbitMqReliabilityConfiguration.LEARNING_QUEUE, 5_000);
            assertThat(message).as("Rabbit delivery %s/%s", index + 1, expected).isNotNull();
            ReliableEventEnvelope envelope = learning.deserialize(new String(message.getBody(), StandardCharsets.UTF_8));
            assertThat((String) message.getMessageProperties().getHeader("eventId")).isEqualTo(envelope.eventId());
            assertThat((String) message.getMessageProperties().getHeader("correlationId")).isEqualTo(envelope.correlationId());
            events.add(envelope);
        }
        return events;
    }

    private ReliableEventEnvelope homework(long courseId, String eventId) throws Exception {
        return new ReliableEventEnvelope(
                eventId, "assessment.homework.published.v2", 2, "assessment-homework", "991", 1,
                Instant.parse("2026-08-31T09:15:30Z"), "b1b6a584-ca64-41af-9a18-5c45f9bd67b5",
                objectMapper.readTree("""
                        {"courseId":"%d","homeworkId":"991","title":"live deferred course roster homework",
                         "deadline":"2026-09-06T16:00:00Z","receiverScope":"COURSE_ACTIVE_STUDENTS",
                         "publishedAt":"2026-08-31T09:15:30Z"}
                        """.formatted(courseId))
        );
    }

    private CurrentUser user(long id, String role) {
        return new CurrentUser(id, role.toLowerCase() + id, role, Set.of());
    }

    private int count(String tableOrTableAndWhere) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableOrTableAndWhere, Integer.class);
    }
}
