package com.onlinejudge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.lrn.service.NotificationCreateCommand;
import com.onlinejudge.lrn.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LRN closure for the retained notification API and Homework publication's v2
 * asynchronous hand-off. Homework notification persistence is intentionally
 * covered by the Learning inbox test, not by the retired in-process callback.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_cross_module_integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class LrnCrossModuleEventIntegrationTest {
    private static final long COURSE_ID = 262L;
    private static final long TEACHER_ID = 5262L;
    private static final long STUDENT_ID = 6261L;
    private static final long SECOND_STUDENT_ID = 6262L;
    private static final long OUTSIDER_ID = 6263L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM lrn_notification_status_log");
        jdbcTemplate.update("DELETE FROM lrn_notification");
        jdbcTemplate.update("DELETE FROM t_hwk_review_log");
        jdbcTemplate.update("DELETE FROM t_hwk_evaluation");
        jdbcTemplate.update("DELETE FROM t_hwk_submission");
        jdbcTemplate.update("DELETE FROM t_hwk_test_case");
        jdbcTemplate.update("DELETE FROM t_hwk_question");
        jdbcTemplate.update("DELETE FROM t_hwk_judge_config");
        jdbcTemplate.update("DELETE FROM t_hwk_homework");
        jdbcTemplate.update("DELETE FROM lab_score_change_log");
        jdbcTemplate.update("DELETE FROM lab_score");
        jdbcTemplate.update("DELETE FROM lab_evaluation_result");
        jdbcTemplate.update("DELETE FROM lab_evaluation");
        jdbcTemplate.update("DELETE FROM lab_report");
        jdbcTemplate.update("DELETE FROM lab_submission");
        jdbcTemplate.update("DELETE FROM lab_testcase");
        jdbcTemplate.update("DELETE FROM lab_experiment");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_chapter");
        jdbcTemplate.update("DELETE FROM crs_course");

        jdbcTemplate.update("""
                INSERT INTO crs_course (id, course_name, description, teacher_id, status)
                VALUES (?, 'LRN cross-module closure', 'issue 262 integration fixture', ?, 'ACTIVE')
                """, COURSE_ID, TEACHER_ID);
        jdbcTemplate.update("""
                INSERT INTO crs_course_member (course_id, user_id, role, join_status, joined_at)
                VALUES
                    (?, ?, 'TEACHER', 'ACTIVE', CURRENT_TIMESTAMP),
                    (?, ?, 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP),
                    (?, ?, 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP),
                    (?, ?, 'STUDENT', 'LEFT', CURRENT_TIMESTAMP)
                """, COURSE_ID, TEACHER_ID,
                COURSE_ID, STUDENT_ID,
                COURSE_ID, SECOND_STUDENT_ID,
                COURSE_ID, OUTSIDER_ID);
    }

    @Test
    void homeworkPublishCommitsAnAssessmentOutboxWithoutAnInProcessLearningSideEffect() throws Exception {
        long homeworkId = createHomework();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/notifications")
                .headers(studentHeaders(STUDENT_ID))
                .param("type", "TASK")
                .param("isRead", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.records", hasSize(0)));

        mockMvc.perform(get("/api/v1/notifications")
                .headers(studentHeaders(SECOND_STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/notifications")
                        .headers(studentHeaders(OUTSIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        Integer outboxCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM assessment_event_outbox
                WHERE aggregate_id = ?
                  AND event_type = 'assessment.homework.published.v2'
                  AND delivery_status = 'PENDING'
                """, Integer.class, String.valueOf(homeworkId));
        org.assertj.core.api.Assertions.assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void crossModuleNotificationReadDeleteAndOwnershipRemainAuditable() throws Exception {
        long labId = createLab();
        notificationService.createNotifications(new NotificationCreateCommand(
                "issue-262-notification-" + labId,
                "LAB_EXPERIMENT_PUBLISHED",
                "TASK",
                COURSE_ID,
                "LAB",
                labId,
                List.of(STUDENT_ID, SECOND_STUDENT_ID),
                "实验已发布",
                "验证通知读取、删除与归属审计",
                1,
                "/courses/" + COURSE_ID + "/labs/" + labId
        ));

        long notificationId = notificationId(STUDENT_ID, "LAB", labId);
        mockMvc.perform(put("/api/v1/notifications/read")
                        .headers(studentHeaders(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "notificationIds", List.of(notificationId),
                                "readAll", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        mockMvc.perform(delete("/api/v1/notifications/{notificationId}", notificationId)
                        .headers(studentHeaders(SECOND_STUDENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-404-04"));

        mockMvc.perform(delete("/api/v1/notifications/{notificationId}", notificationId)
                        .headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        mockMvc.perform(get("/api/v1/notifications")
                        .headers(studentHeaders(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/notifications")
                        .headers(studentHeaders(SECOND_STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        Integer transitionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification_status_log
                WHERE notification_id = ?
                  AND user_id = ?
                  AND operation_type IN ('CREATE', 'MARK_READ', 'DELETE')
                """, Integer.class, notificationId, STUDENT_ID);
        org.assertj.core.api.Assertions.assertThat(transitionCount).isEqualTo(3);
    }

    private long createLab() throws Exception {
        Map<String, Object> payload = Map.ofEntries(
                entry("title", "Issue 262 real lab event"),
                entry("description", "Verify LAB to LRN persistence"),
                entry("deadline", futureDeadline()),
                entry("maxScore", 100),
                entry("attachmentIds", List.of()),
                entry("allowedLanguages", "java,python"),
                entry("evaluationMode", "DOCKER_IO"),
                entry("autoEvaluate", true),
                entry("reportRequired", false),
                entry("timeLimitMs", 60000),
                entry("memoryLimitKb", 262144),
                entry("testcases", List.of())
        );
        String response = mockMvc.perform(post("/api/v1/courses/{courseId}/labs", COURSE_ID)
                        .headers(teacherHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long createHomework() throws Exception {
        Map<String, Object> payload = Map.ofEntries(
                entry("courseId", COURSE_ID),
                entry("title", "Issue 262 real homework event"),
                entry("description", "Verify HWK to LRN persistence"),
                entry("type", "TEXT"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("questions", List.of()),
                entry("testCases", List.of())
        );
        String response = mockMvc.perform(post("/api/v1/homeworks")
                        .headers(teacherHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long notificationId(long userId, String sourceModule, long sourceId) {
        Long id = jdbcTemplate.queryForObject("""
                SELECT id
                FROM lrn_notification
                WHERE user_id = ? AND source_module = ? AND source_id = ?
                """, Long.class, userId, sourceModule, sourceId);
        return id == null ? 0L : id;
    }

    private String futureDeadline() {
        return LocalDateTime.now().plusDays(30).withNano(0).toString();
    }

    private HttpHeaders teacherHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", Long.toString(TEACHER_ID));
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Course-Ids", Long.toString(COURSE_ID));
        headers.add("X-Manageable-Course-Ids", Long.toString(COURSE_ID));
        headers.add("X-Course-Student-Ids", COURSE_ID + ":" + STUDENT_ID + "," + SECOND_STUDENT_ID);
        headers.add("X-Course-Teacher-Ids", COURSE_ID + ":" + TEACHER_ID);
        return headers;
    }

    private HttpHeaders studentHeaders(long studentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", Long.toString(studentId));
        headers.add("X-User-Role", "STUDENT");
        if (studentId != OUTSIDER_ID) {
            headers.add("X-Course-Ids", Long.toString(COURSE_ID));
        }
        headers.add("X-Course-Teacher-Ids", COURSE_ID + ":" + TEACHER_ID);
        return headers;
    }
}
