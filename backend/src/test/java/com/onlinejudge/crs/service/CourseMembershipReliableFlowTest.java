package com.onlinejudge.crs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.EventProcessingDecision;
import com.onlinejudge.common.reliability.ConfirmedEventPublisher;
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Course owns the membership truth.  This deliberately exercises the real
 * Course commands rather than pre-populating Learning's projection, so a
 * missing Course producer cannot be hidden by consumer-only fixtures.
 */
@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:course_membership_reliable_flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
        CourseMembershipReliableFlowTest.TestConfig.class
})
class CourseMembershipReliableFlowTest {
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RecordingConfirmedPublisher courseRecordingPublisher() {
            return new RecordingConfirmedPublisher();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CourseService courses;

    @org.springframework.beans.factory.annotation.Autowired
    private LearningReliableEventConsumer learning;

    @org.springframework.beans.factory.annotation.Autowired
    private CourseEventOutboxRepository outbox;

    @org.springframework.beans.factory.annotation.Autowired
    private RecordingConfirmedPublisher publisher;

    @org.springframework.beans.factory.annotation.Autowired
    private LearningReconciliationWorker reconciliation;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
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
        publisher.envelopes.clear();
    }

    @Test
    void realCourseMembershipCommandsWriteRosterFactsThatReleaseOneDeferredHomeworkThenReflectRemoval() throws Exception {
        CurrentUser teacher = user(501L, "TEACHER");
        CurrentUser student = user(601L, "STUDENT");
        CourseResponse course = courses.create(new CourseCreateRequest(
                "Course-owned reliable roster", "", "2026-F", "SE", null,
                EnrollmentMode.PUBLIC, null, null, null, null, CourseStatus.ACTIVE
        ), teacher);

        ReliableEventEnvelope homework = homework(course.id(), "homework-before-course-roster");
        assertThat(learning.consume(homework)).isEqualTo(EventProcessingDecision.ACK);
        assertThat(count("learning_deferred_event")).isEqualTo(1);
        assertThat(count("learning_course_membership_watermark")).isZero();

        courses.join(course.id(), new CourseJoinRequest(null, ""), student);

        // RED before #337's producer implementation: Course mutations leave
        // this table empty, so Learning can never receive a complete roster.
        assertThat(count("course_event_outbox WHERE event_type = 'course.membership.snapshot.v2'"))
                .isEqualTo(2);
        assertThat(count("course_event_outbox WHERE event_type = 'course.member.changed.v2'"))
                .isEqualTo(1);

        deliverCourseOutbox();
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(2L);
        assertThat(reconciliation.reconcileDue(Instant.parse("2030-01-01T00:00:00Z"))).isEqualTo(1);
        assertThat(count("lrn_notification WHERE user_id = 601")).isEqualTo(1);
        assertThat(count("learning_deferred_event WHERE delivery_status = 'RESOLVED'")).isEqualTo(1);

        courses.removeMember(course.id(), student.id(), teacher);
        assertThat(count("course_event_outbox WHERE event_type = 'course.membership.snapshot.v2'"))
                .isEqualTo(3);
        assertThat(count("course_event_outbox WHERE event_type = 'course.member.changed.v2'"))
                .isEqualTo(2);
        deliverCourseOutbox();
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                "SELECT membership_status FROM learning_course_member_projection WHERE course_id = ? AND user_id = ?",
                String.class, course.id(), student.id())).isEqualTo(CourseMemberStatus.REMOVED.name());
    }

    @Test
    void sourceOwnedBootstrapCheckpointsARecoveredLegacyCourseOnceAndReleasesItsDeferredHomework() throws Exception {
        CurrentUser teacher = user(701L, "TEACHER");
        CurrentUser student = user(801L, "STUDENT");
        CourseResponse course = courses.create(new CourseCreateRequest(
                "Recovered legacy Course roster", "", "2026-F", "SE", null,
                EnrollmentMode.PUBLIC, null, null, null, null, CourseStatus.ACTIVE
        ), teacher);
        courses.join(course.id(), new CourseJoinRequest(null, ""), student);
        jdbc.update("DELETE FROM course_event_outbox WHERE aggregate_id = ? OR aggregate_id LIKE ?",
                String.valueOf(course.id()), course.id() + ":%");

        ReliableEventEnvelope homework = homework(course.id(), "homework-before-source-bootstrap");
        assertThat(learning.consume(homework)).isEqualTo(EventProcessingDecision.ACK);
        assertThat(count("learning_deferred_event")).isEqualTo(1);

        CourseMembershipBootstrapper bootstrapper = new CourseMembershipBootstrapper(outbox, 10);
        assertThat(bootstrapper.bootstrapMissingRosters()).isEqualTo(1);
        assertThat(bootstrapper.bootstrapMissingRosters()).isZero();
        assertThat(count("course_event_outbox WHERE event_type = 'course.membership.snapshot.v2'"))
                .isEqualTo(1);

        deliverCourseOutbox();
        assertThat(reconciliation.reconcileDue(Instant.parse("2030-01-01T00:00:00Z"))).isEqualTo(1);
        assertThat(count("lrn_notification WHERE user_id = 801")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(1L);
    }

    @Test
    void sourceOwnedReconciliationReissuesTheNextRosterVersionAfterLearningProjectionRestoreOnce() throws Exception {
        CurrentUser teacher = user(901L, "TEACHER");
        CurrentUser student = user(1001L, "STUDENT");
        CourseResponse course = courses.create(new CourseCreateRequest(
                "Recovered Learning projection", "", "2026-F", "SE", null,
                EnrollmentMode.PUBLIC, null, null, null, null, CourseStatus.ACTIVE
        ), teacher);
        courses.join(course.id(), new CourseJoinRequest(null, ""), student);
        deliverCourseOutbox();
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(2L);

        jdbc.update("DELETE FROM lrn_notification_status_log");
        jdbc.update("DELETE FROM lrn_notification");
        jdbc.update("DELETE FROM learning_event_inbox");
        jdbc.update("DELETE FROM learning_course_member_projection");
        jdbc.update("DELETE FROM learning_course_membership_watermark");
        ReliableEventEnvelope homework = homework(course.id(), "homework-after-learning-restore");
        assertThat(learning.consume(homework)).isEqualTo(EventProcessingDecision.ACK);
        assertThat(count("learning_deferred_event")).isEqualTo(1);

        CourseMembershipBootstrapper bootstrapper = new CourseMembershipBootstrapper(outbox, 10);
        Instant recoveryTime = Instant.parse("2030-01-01T00:00:00Z");
        assertThat(bootstrapper.reconcilePublishedRosters(recoveryTime)).isEqualTo(1);
        assertThat(bootstrapper.reconcilePublishedRosters(recoveryTime)).isZero();
        assertThat(count("course_event_outbox WHERE event_type = 'course.membership.snapshot.v2' "
                + "AND aggregate_version = 3")).isEqualTo(1);

        deliverCourseOutbox();
        assertThat(reconciliation.reconcileDue(recoveryTime.plusSeconds(1))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, course.id())).isEqualTo(3L);
        assertThat(count("lrn_notification WHERE user_id = 1001")).isEqualTo(1);
    }

    private void deliverCourseOutbox() {
        CourseOutboxPublisher productionPublisher = new CourseOutboxPublisher(
                outbox, publisher, 50, 3, 30, 1, 16);
        int expected = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_event_outbox WHERE delivery_status = 'PENDING'", Integer.class);
        int alreadyPublished = count("course_event_outbox WHERE delivery_status = 'PUBLISHED'");
        assertThat(productionPublisher.drain(Instant.parse("2026-08-31T09:16:00Z"))).isEqualTo(expected);
        for (ReliableEventEnvelope envelope : publisher.envelopes) {
            assertThat(learning.consume(envelope)).isEqualTo(EventProcessingDecision.ACK);
        }
        assertThat(count("course_event_outbox WHERE delivery_status = 'PUBLISHED'")).isEqualTo(alreadyPublished + expected);
        publisher.envelopes.clear();
    }

    private ReliableEventEnvelope homework(long courseId, String eventId) throws Exception {
        return new ReliableEventEnvelope(
                eventId, "assessment.homework.published.v2", 2, "assessment-homework", "991", 1,
                Instant.parse("2026-08-31T09:15:30Z"), "a4c25a36-c5ec-4bdf-8afd-ddc05f4ea13e",
                objectMapper.readTree("""
                        {"courseId":"%d","homeworkId":"991","title":"deferred course roster homework",
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

    static class RecordingConfirmedPublisher implements ConfirmedEventPublisher {
        private final List<ReliableEventEnvelope> envelopes = new java.util.ArrayList<>();

        @Override
        public void publish(ReliableEventEnvelope envelope, String routingKey) {
            envelopes.add(envelope);
        }
    }
}
