package com.onlinejudge.courseservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.learning.LrnEventProjection;
import com.onlinejudge.courseservice.security.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #355 LRN fold-in against the #306 frozen Course-owned tables: notifications,
 * learning tasks/records/progress and the idempotent fact projection all live
 * inside the Course service, and receiver resolution is gated by the complete
 * roster watermark.
 */
@SpringBootTest(classes = CourseServiceApplication.class)
@AutoConfigureMockMvc
class LrnFoldServiceTest {
    private static final KeyPair KEY_PAIR = TestJwtFactory.rsaKeyPair();
    private static final String BOOTSTRAP_JWKS = TestJwtFactory.jwks("course-test-kid", KEY_PAIR);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LrnEventProjection projection;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("course.identity.jwks-trust-bundle", () -> BOOTSTRAP_JWKS);
        registry.add("course.identity.jwks-uri", () -> "http://127.0.0.1:1/identity/jwks.json");
        registry.add("course.identity.refresh-enabled", () -> false);
        registry.add("course.identity.mtls-service-subjects", () -> "CN=course-service");
        registry.add("onlinejudge.notifications.internal-token", () -> "lrn-test-token");
    }

    @BeforeEach
    void cleanFacts() {
        jdbcTemplate.update("DELETE FROM lrn_learning_record");
        jdbcTemplate.update("DELETE FROM lrn_learning_progress");
        jdbcTemplate.update("DELETE FROM lrn_learning_task");
        jdbcTemplate.update("DELETE FROM lrn_notification_status_log");
        jdbcTemplate.update("DELETE FROM lrn_notification");
        jdbcTemplate.update("DELETE FROM lrn_reminder_rule");
        jdbcTemplate.update("DELETE FROM lrn_notification_setting");
        jdbcTemplate.update("DELETE FROM lrn_reminder_scan_log");
        jdbcTemplate.update("DELETE FROM learning_event_inbox");
        jdbcTemplate.update("DELETE FROM learning_event_delivery_attempt");
        jdbcTemplate.update("DELETE FROM learning_event_dead_letter");
        jdbcTemplate.update("DELETE FROM learning_event_reconciliation_request");
        jdbcTemplate.update("DELETE FROM learning_deferred_event");
        jdbcTemplate.update("DELETE FROM learning_course_member_projection");
        jdbcTemplate.update("DELETE FROM learning_course_membership_watermark");
        jdbcTemplate.update("DELETE FROM course_file_delete_journal");
        jdbcTemplate.update("DELETE FROM course_event_outbox");
        jdbcTemplate.update("DELETE FROM course_membership_reconciliation_checkpoint");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_announcement");
        jdbcTemplate.update("DELETE FROM crs_resource");
        CourseTestDataCleanup.deleteChapters(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM crs_course");
    }

    @Test
    void duplicateHomeworkFactAppliesOneTaskAndOneNotificationPerActiveMember() throws Exception {
        String courseId = createCourseWithRosterWatermark("801", "802");
        String envelope = homeworkEnvelope(courseId);

        projection.consume(envelope);
        projection.consume(envelope);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM learning_event_inbox
                 WHERE consumer_name = 'course-lrn' AND event_id = '0b277609-059f-4bd2-b26d-f54341003ecc'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_learning_task WHERE user_id = 802 AND course_id = ?",
                Integer.class, Long.parseLong(courseId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_notification WHERE user_id = 802",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM lrn_learning_task WHERE user_id = 802", String.class))
                .isEqualTo("Java collections homework");
    }

    @Test
    void homeworkFactBeforeCompleteRosterWatermarkFailsClosedWithReconciliationGap() throws Exception {
        String teacher = userToken("803", List.of("TEACHER"));
        String courseResponse = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", teacher)
                        .header("X-Request-Id", "5d2ff3f0-0e29-4b5f-9432-cc9ec51ac701")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"gap course\",\"enrollmentMode\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(courseResponse).at("/data/id").asText();

        projection.consume(homeworkEnvelope(courseId));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_learning_task", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_notification", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM learning_event_reconciliation_request
                 WHERE consumer_name = 'course-lrn' AND aggregate_type = 'course-membership-roster'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void homeworkFactBeforeRosterIsDurablyDeferredAndReplayedExactlyOnceAfterTheSnapshotCatchesUp() throws Exception {
        String courseId = createCourse("831", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac901");
        String envelope = homeworkEnvelope(courseId);

        projection.consume(envelope);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_learning_task", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_notification", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM learning_deferred_event
                 WHERE consumer_name = 'course-lrn' AND event_id = '0b277609-059f-4bd2-b26d-f54341003ecc'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delivery_status FROM learning_deferred_event
                 WHERE consumer_name = 'course-lrn' AND event_id = '0b277609-059f-4bd2-b26d-f54341003ecc'
                """, String.class)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT request_status FROM learning_event_reconciliation_request
                 WHERE consumer_name = 'course-lrn' AND aggregate_type = 'course-membership-roster'
                """, String.class)).isEqualTo("OPEN");

        projection.consume(membershipSnapshotEnvelope(courseId, "831", "832"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_learning_task WHERE user_id = 832 AND course_id = ?",
                Integer.class, Long.parseLong(courseId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_notification WHERE user_id = 832", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delivery_status FROM learning_deferred_event
                 WHERE consumer_name = 'course-lrn' AND event_id = '0b277609-059f-4bd2-b26d-f54341003ecc'
                """, String.class)).isEqualTo("RESOLVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT request_status FROM learning_event_reconciliation_request
                 WHERE consumer_name = 'course-lrn' AND aggregate_type = 'course-membership-roster'
                """, String.class)).isEqualTo("RESOLVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM learning_event_inbox
                 WHERE consumer_name = 'course-lrn' AND event_id = '0b277609-059f-4bd2-b26d-f54341003ecc'
                """, Integer.class)).isEqualTo(1);

        // Snapshot or homework redelivery must never duplicate the replayed side effects.
        projection.consume(membershipSnapshotEnvelope(courseId, "831", "832"));
        projection.consume(envelope);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_learning_task WHERE user_id = 832 AND course_id = ?",
                Integer.class, Long.parseLong(courseId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_notification WHERE user_id = 832", Integer.class)).isEqualTo(1);
    }

    @Test
    void outOfOrderMemberFactsNeverRollBackTheProjectionAndVersionGapsEnterReconciliation() throws Exception {
        String courseId = createCourse("833", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac911");

        projection.consume(memberChangedEnvelope(courseId, "834", "REMOVED", 3, "member-remove-v3"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT membership_status FROM learning_course_member_projection WHERE course_id = ? AND user_id = 834
                """, String.class, Long.parseLong(courseId))).isEqualTo("REMOVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT member_version FROM learning_course_member_projection WHERE course_id = ? AND user_id = 834
                """, Long.class, Long.parseLong(courseId))).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM learning_event_reconciliation_request
                 WHERE consumer_name = 'course-lrn' AND aggregate_type = 'course-member'
                   AND aggregate_id = ? AND request_status = 'OPEN'
                """, Integer.class, courseId + ":834")).isEqualTo(1);

        projection.consume(memberChangedEnvelope(courseId, "834", "ACTIVE", 2, "member-active-v2"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT membership_status FROM learning_course_member_projection WHERE course_id = ? AND user_id = 834
                """, String.class, Long.parseLong(courseId))).isEqualTo("REMOVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT member_version FROM learning_course_member_projection WHERE course_id = ? AND user_id = 834
                """, Long.class, Long.parseLong(courseId))).isEqualTo(3L);

        // The authoritative snapshot covers the missing versions and closes the member gap.
        projection.consume("""
                {
                  "eventId": "5a10fd0e-0000-4f5b-9432-000000000003",
                  "eventType": "course.membership.snapshot.v2",
                  "payloadVersion": 2,
                  "aggregateType": "course-membership-roster",
                  "aggregateId": "%s",
                  "aggregateVersion": 2,
                  "occurredAt": "2026-08-30T09:30:00Z",
                  "correlationId": "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5d",
                  "payload": {
                    "courseId": "course-%s",
                    "rosterVersion": 2,
                    "members": [
                      {"userId": "834", "membershipStatus": "REMOVED", "memberVersion": 3}
                    ]
                  }
                }
                """.formatted(courseId, courseId));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT request_status FROM learning_event_reconciliation_request
                 WHERE consumer_name = 'course-lrn' AND aggregate_type = 'course-member'
                   AND aggregate_id = ?
                """, String.class, courseId + ":834")).isEqualTo("RESOLVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT membership_status FROM learning_course_member_projection WHERE course_id = ? AND user_id = 834
                """, String.class, Long.parseLong(courseId))).isEqualTo("REMOVED");
    }

    @Test
    void deferredHomeworkConvergesWithZeroSideEffectsWhenTheSnapshotRosterHasNoActiveStudents() throws Exception {
        String courseId = createCourse("835", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac921");

        projection.consume(homeworkEnvelope(courseId));
        projection.consume("""
                {
                  "eventId": "5a10fd0e-0000-4f5b-9432-000000000004",
                  "eventType": "course.membership.snapshot.v2",
                  "payloadVersion": 2,
                  "aggregateType": "course-membership-roster",
                  "aggregateId": "%s",
                  "aggregateVersion": 1,
                  "occurredAt": "2026-08-30T09:40:00Z",
                  "correlationId": "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5e",
                  "payload": {
                    "courseId": "course-%s",
                    "rosterVersion": 1,
                    "members": []
                  }
                }
                """.formatted(courseId, courseId));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_learning_task", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_notification", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delivery_status FROM learning_deferred_event
                 WHERE consumer_name = 'course-lrn' AND event_id = '0b277609-059f-4bd2-b26d-f54341003ecc'
                """, String.class)).isEqualTo("RESOLVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT request_status FROM learning_event_reconciliation_request
                 WHERE consumer_name = 'course-lrn' AND aggregate_type = 'course-membership-roster'
                """, String.class)).isEqualTo("RESOLVED");
    }

    @Test
    void staleMembershipSnapshotCannotDowngradeTheRosterWatermark() throws Exception {
        String courseId = createCourseWithRosterWatermark("805", "806");

        // A later complete snapshot (rosterVersion 2) with a new member advances the watermark.
        String newer = """
                {
                  "eventId": "5a10fd0e-0000-4f5b-9432-000000000002",
                  "eventType": "course.membership.snapshot.v2",
                  "payloadVersion": 2,
                  "aggregateType": "course-membership-roster",
                  "aggregateId": "%s",
                  "aggregateVersion": 2,
                  "occurredAt": "2026-08-30T09:05:00Z",
                  "correlationId": "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5c",
                  "payload": {
                    "courseId": "course-%s",
                    "rosterVersion": 2,
                    "members": [
                      {"userId": "805", "membershipStatus": "ACTIVE", "memberVersion": 1},
                      {"userId": "806", "membershipStatus": "ACTIVE", "memberVersion": 1},
                      {"userId": "807", "membershipStatus": "ACTIVE", "memberVersion": 1}
                    ]
                  }
                }
                """.formatted(courseId, courseId);
        projection.consume(newer);

        // A stale replay of the version-1 snapshot must not downgrade watermark or roster.
        projection.consume(membershipSnapshotEnvelope(courseId, "805", "806"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?",
                Long.class, Long.parseLong(courseId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_course_member_projection WHERE course_id = ?",
                Integer.class, Long.parseLong(courseId))).isEqualTo(3);
    }

    @Test
    void notificationEventEntryIsIdempotentByEventKey() throws Exception {
        String payload = """
                {
                  "idempotencyKey": "homework-published-77",
                  "eventType": "assessment.homework.published.v2",
                  "notificationType": "TASK",
                  "sourceModule": "HWK",
                  "sourceId": 77,
                  "receiverUserIds": [802],
                  "title": "Homework 77",
                  "content": "已发布",
                  "priority": 1,
                  "actionUrl": "/learning/tasks"
                }
                """;
        mockMvc.perform(post("/api/v1/notifications/events")
                        .header("X-Internal-Token", "lrn-test-token")
                        .header("X-Request-Id", "6e22a1f3-6a1a-4f9d-9d84-5a2d3e1f0b01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1));
        mockMvc.perform(post("/api/v1/notifications/events")
                        .header("X-Internal-Token", "lrn-test-token")
                        .header("X-Request-Id", "6e22a1f3-6a1a-4f9d-9d84-5a2d3e1f0b02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(0));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lrn_notification", Integer.class)).isEqualTo(1);
    }

    @Test
    void learningProgressRecordTaskAndReminderApisRoundTrip() throws Exception {
        String teacher = userToken("811", List.of("TEACHER"));
        String student = userToken("812", List.of("STUDENT"));
        String courseResponse = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", teacher)
                        .header("X-Request-Id", "7d2ff3f0-0e29-4b5f-9432-cc9ec51ac731")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"lrn round trip\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(courseResponse).at("/data/id").asText();
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student)
                        .header("X-Request-Id", "7d2ff3f0-0e29-4b5f-9432-cc9ec51ac732"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/learning/progress")
                        .header("Authorization", student)
                        .header("X-Request-Id", "7d2ff3f0-0e29-4b5f-9432-cc9ec51ac733")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"sourceModule\":\"HWK\",\"sourceId\":77,\"progressPercent\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value(30));

        mockMvc.perform(get("/api/v1/learning/progress?courseId=" + courseId)
                        .header("Authorization", student)
                        .header("X-Request-Id", "7d2ff3f0-0e29-4b5f-9432-cc9ec51ac734"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.courses[0].progressPercent").value(30));

        mockMvc.perform(post("/api/v1/learning/records")
                        .header("Authorization", student)
                        .header("X-Request-Id", "7d2ff3f0-0e29-4b5f-9432-cc9ec51ac735")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"sourceModule\":\"HWK\",\"sourceId\":77,\"actionType\":\"ACCESS\",\"durationSeconds\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionType").value("ACCESS"));

        mockMvc.perform(get("/api/v1/learning/statistics?courseId=" + courseId)
                        .header("Authorization", student)
                        .header("X-Request-Id", "7d2ff3f0-0e29-4b5f-9432-cc9ec51ac736"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalRecordCount").value(1))
                .andExpect(jsonPath("$.data.summary.totalDurationSeconds").value(60));

        mockMvc.perform(put("/api/v1/reminder-rules")
                        .header("Authorization", student)
                        .header("X-Request-Id", "7d2ff3f0-0e29-4b5f-9432-cc9ec51ac737")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[{\"reminderType\":\"HOMEWORK_DEADLINE\",\"sourceModule\":\"HWK\",\"aheadMinutes\":60,\"enabled\":false,\"required\":false}],\"settings\":{\"enableExperiment\":false,\"enableHomework\":true,\"enableGrade\":true,\"enableAnnouncement\":true,\"enableNonCriticalReminder\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules[0].aheadMinutes").value(60))
                .andExpect(jsonPath("$.data.rules[0].enabled").value(false));
    }

    @Test
    void internalLearningTasksAndReconciliationRequestsFollowTheFrozenContract() throws Exception {
        String courseId = createCourseWithRosterWatermark("821", "822");
        projection.consume(homeworkEnvelope(courseId));

        mockMvc.perform(get("/internal/v2/learning/tasks/recent?courseId=" + courseId + "&userId=822&limit=5")
                        .header("X-Request-Id", "8d2ff3f0-0e29-4b5f-9432-cc9ec51ac801")
                        .header("X-OnlineJudge-Service-Authorization",
                                serviceToken("assessment-api", List.of("learning.tasks.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Java collections homework"))
                .andExpect(jsonPath("$.size").value(5));

        mockMvc.perform(get("/internal/v2/learning/tasks/recent?courseId=" + courseId + "&userId=822&limit=5")
                        .header("X-Request-Id", "8d2ff3f0-0e29-4b5f-9432-cc9ec51ac802")
                        .header("X-OnlineJudge-Service-Authorization",
                                serviceToken("assessment-api", List.of("course.members.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_FORBIDDEN"));

        String body = "{\"sourceService\":\"assessment\",\"eventId\":\"0b277609-059f-4bd2-b26d-f54341003ecc\",\"reason\":\"PROJECTION_GAP\"}";
        mockMvc.perform(post("/internal/v2/notifications/reconciliation-requests")
                        .header("X-Request-Id", "8d2ff3f0-0e29-4b5f-9432-cc9ec51ac803")
                        .header("Idempotency-Key", "reconcile-homework-77-0001")
                        .header("X-OnlineJudge-Service-Authorization",
                                serviceToken("assessment-api", List.of("notification-reconciliation")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/internal/v2/notifications/reconciliation-requests")
                        .header("X-Request-Id", "8d2ff3f0-0e29-4b5f-9432-cc9ec51ac804")
                        .header("Idempotency-Key", "reconcile-homework-77-0001")
                        .header("X-OnlineJudge-Service-Authorization",
                                serviceToken("assessment-api", List.of("notification-reconciliation")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_IDEMPOTENCY_CONFLICT"));
    }

    private String createCourseWithRosterWatermark(String teacherId, String studentId) throws Exception {
        String courseId = createCourse(teacherId, "5d2ff3f0-0e29-4b5f-9432-cc9ec51ac711");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", userToken(studentId, List.of("STUDENT")))
                        .header("X-Request-Id", "5d2ff3f0-0e29-4b5f-9432-cc9ec51ac712"))
                .andExpect(status().isOk());
        projection.consume(membershipSnapshotEnvelope(courseId, teacherId, studentId));
        return courseId;
    }

    private String createCourse(String teacherId, String requestId) throws Exception {
        String courseResponse = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", userToken(teacherId, List.of("TEACHER")))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"fact projection course\",\"enrollmentMode\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(courseResponse).at("/data/id").asText();
    }

    private String memberChangedEnvelope(String courseId, String userId, String status,
                                         long memberVersion, String eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "course.member.changed.v2",
                  "payloadVersion": 2,
                  "aggregateType": "course-member",
                  "aggregateId": "%s:%s",
                  "aggregateVersion": %d,
                  "occurredAt": "2026-08-30T09:20:00Z",
                  "correlationId": "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5c",
                  "payload": {
                    "courseId": "%s",
                    "userId": "%s",
                    "membershipStatus": "%s",
                    "memberVersion": %d
                  }
                }
                """.formatted(eventId, courseId, userId, memberVersion, courseId, userId, status, memberVersion);
    }

    private String membershipSnapshotEnvelope(String courseId, String teacherId, String studentId) {
        return """
                {
                  "eventId": "5a10fd0e-0000-4f5b-9432-000000000001",
                  "eventType": "course.membership.snapshot.v2",
                  "payloadVersion": 2,
                  "aggregateType": "course-membership-roster",
                  "aggregateId": "%s",
                  "aggregateVersion": 1,
                  "occurredAt": "2026-08-30T09:00:00Z",
                  "correlationId": "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5a",
                  "payload": {
                    "courseId": "course-%s",
                    "rosterVersion": 1,
                    "members": [
                      {"userId": "%s", "membershipStatus": "ACTIVE", "memberVersion": 1},
                      {"userId": "%s", "membershipStatus": "ACTIVE", "memberVersion": 1}
                    ]
                  }
                }
                """.formatted(courseId, courseId, teacherId, studentId);
    }

    private String homeworkEnvelope(String courseId) {
        return """
                {
                  "eventId": "0b277609-059f-4bd2-b26d-f54341003ecc",
                  "eventType": "assessment.homework.published.v2",
                  "payloadVersion": 2,
                  "aggregateType": "assessment-homework",
                  "aggregateId": "homework-77",
                  "aggregateVersion": 4,
                  "occurredAt": "2026-08-30T09:15:30Z",
                  "correlationId": "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5b",
                  "payload": {
                    "courseId": "course-%s",
                    "homeworkId": "homework-77",
                    "title": "Java collections homework",
                    "deadline": "2026-09-06T16:00:00Z",
                    "receiverScope": "COURSE_ACTIVE_STUDENTS",
                    "publishedAt": "2026-08-30T09:15:30Z"
                  }
                }
                """.formatted(courseId);
    }

    private String userToken(String userId, List<String> roles) {
        return TestJwtFactory.userToken(KEY_PAIR, "course-test-kid", userId, roles, List.of("course:manage"));
    }

    private String serviceToken(String subject, List<String> scopes) {
        return TestJwtFactory.serviceToken(KEY_PAIR, "course-test-kid", subject, "course", scopes);
    }
}
