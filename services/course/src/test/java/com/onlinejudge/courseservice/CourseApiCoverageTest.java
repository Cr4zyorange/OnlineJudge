package com.onlinejudge.courseservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.learning.LrnEventProjection;
import com.onlinejudge.courseservice.security.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #367 API coverage: one dedicated contract test per Course endpoint the
 * existing service suite did not exercise through HTTP.  Fact setup reuses the
 * Course-owned LRN projection pattern from LrnFoldServiceTest.
 */
@SpringBootTest(classes = CourseServiceApplication.class)
@AutoConfigureMockMvc
class CourseApiCoverageTest {
    private static final KeyPair KEY_PAIR = TestJwtFactory.rsaKeyPair();
    private static final String BOOTSTRAP_JWKS = TestJwtFactory.jwks("course-coverage-kid", KEY_PAIR);

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
        registry.add("onlinejudge.notifications.internal-token", () -> "course-coverage-token");
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
    void membersAndStudentsEndpointsReturnRoleScopedRoster() throws Exception {
        String teacher = userToken("901", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "roster coverage");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", userToken("902", List.of("STUDENT")))
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac901"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/{courseId}/members", courseId)
                        .header("Authorization", teacher)
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac902"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.items[*].userId", hasItem("902")))
                .andExpect(jsonPath("$.data.items[*].role", hasItem("STUDENT")));

        mockMvc.perform(get("/api/v1/courses/{courseId}/members?role=TEACHER", courseId)
                        .header("Authorization", teacher)
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac903"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].role", hasItem("TEACHER")))
                .andExpect(jsonPath("$.data.items[?(@.role=='TEACHER')].userId", hasItem("901")));

        mockMvc.perform(get("/api/v1/courses/{courseId}/students", courseId)
                        .header("Authorization", teacher)
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac904"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.items[*].role", hasItem("STUDENT")))
                .andExpect(jsonPath("$.data.items[?(@.role!='STUDENT')]").isEmpty());
    }

    @Test
    void rosterReadEndpointsRejectMissingBearer() throws Exception {
        mockMvc.perform(get("/api/v1/courses/1/members")
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac905"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void learningTasksAndTeacherProgressEndpointsFollowCourseMemberContract() throws Exception {
        String courseId = createCourseWithRosterWatermark("911", "912");

        mockMvc.perform(get("/api/v1/learning/tasks?courseId=" + courseId)
                        .header("Authorization", userToken("912", List.of("STUDENT")))
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac906"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.page").isNumber())
                .andExpect(jsonPath("$.data.size").isNumber());

        mockMvc.perform(get("/api/v1/learning/progress/teacher?courseId=" + courseId)
                        .header("Authorization", userToken("911", List.of("TEACHER")))
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac907"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courseId").value(Long.parseLong(courseId)));
    }

    @Test
    void notificationsListReadAndDeleteEndpointsReturnMutationResults() throws Exception {
        String courseId = createCourseWithRosterWatermark("921", "922");
        jdbcTemplate.update("""
                INSERT INTO lrn_notification (user_id, course_id, idempotency_key, title, content, type, priority,
                                              is_read, source_module, source_id, created_at)
                VALUES (922, ?, 'coverage-notification-1', '覆盖测试通知', '内容', 'SYSTEM_ANNOUNCEMENT', 1,
                        FALSE, 'SYS', 1, CURRENT_TIMESTAMP)
                """, Long.parseLong(courseId));
        String student = userToken("922", List.of("STUDENT"));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", student)
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac908"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[*].title", hasItem("覆盖测试通知")));

        mockMvc.perform(put("/api/v1/notifications/read")
                        .header("Authorization", student)
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac909")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readAll\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        mockMvc.perform(delete("/api/v1/notifications/{notificationId}", 1L)
                        .header("Authorization", student)
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac910"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void reminderRulesEndpointReturnsStoredOverview() throws Exception {
        String student = userToken("932", List.of("STUDENT"));

        mockMvc.perform(get("/api/v1/reminder-rules")
                        .header("Authorization", student)
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac911"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.rules").isArray());
    }

    @Test
    void versionEndpointIsPublicAndIdentifiesCourseService() throws Exception {
        mockMvc.perform(get("/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("course-service"))
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.revision").isNotEmpty());
    }

    private String createdCourse(String teacherToken, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", teacherToken)
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac912")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"enrollmentMode\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asText();
    }

    private String createCourseWithRosterWatermark(String teacherId, String studentId) throws Exception {
        String courseId = createdCourse(userToken(teacherId, List.of("TEACHER")), "coverage roster");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", userToken(studentId, List.of("STUDENT")))
                        .header("X-Request-Id", "9d2ff3f0-0e29-4b5f-9432-cc9ec51ac913"))
                .andExpect(status().isOk());
        projection.consume(membershipSnapshotEnvelope(courseId, teacherId, studentId));
        return courseId;
    }

    private String membershipSnapshotEnvelope(String courseId, String teacherId, String studentId) {
        return """
                {
                  "eventId": "5a10fd0e-0000-4f5b-9432-000000000091",
                  "eventType": "course.membership.snapshot.v2",
                  "payloadVersion": 2,
                  "aggregateType": "course-membership-roster",
                  "aggregateId": "%s",
                  "aggregateVersion": 1,
                  "occurredAt": "2026-09-02T00:00:00Z",
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

    private String userToken(String userId, List<String> roles) {
        return TestJwtFactory.userToken(KEY_PAIR, "course-coverage-kid", userId, roles, List.of("course:manage"));
    }
}
