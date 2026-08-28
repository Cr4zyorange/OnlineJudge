package com.onlinejudge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:grd_lrn_integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class GrdLrnIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM lrn_notification_status_log");
        jdbcTemplate.update("DELETE FROM lrn_notification");
        jdbcTemplate.update("DELETE FROM t_course_grade_summary");
        jdbcTemplate.update("DELETE FROM t_grade_review_request");
        jdbcTemplate.update("DELETE FROM t_grade_change_log");
        jdbcTemplate.update("DELETE FROM t_grade_publish_record");
        jdbcTemplate.update("DELETE FROM t_grade_record");
        jdbcTemplate.update("DELETE FROM t_grade_item");
        jdbcTemplate.update("DELETE FROM t_grade_calculation_batch");
        jdbcTemplate.update("DELETE FROM t_hwk_review_log");
        jdbcTemplate.update("DELETE FROM t_hwk_evaluation");
        jdbcTemplate.update("DELETE FROM t_hwk_submission");
        jdbcTemplate.update("DELETE FROM t_hwk_test_case");
        jdbcTemplate.update("DELETE FROM t_hwk_question");
        jdbcTemplate.update("DELETE FROM t_hwk_judge_config");
        jdbcTemplate.update("DELETE FROM t_hwk_homework");
        jdbcTemplate.update("DELETE FROM lab_score_change_log");
        jdbcTemplate.update("DELETE FROM lab_score");
        jdbcTemplate.update("DELETE FROM lab_submission");
        jdbcTemplate.update("DELETE FROM lab_testcase");
        jdbcTemplate.update("DELETE FROM lab_report");
        jdbcTemplate.update("DELETE FROM lab_experiment");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_chapter");
        jdbcTemplate.update("DELETE FROM crs_course");

        insertCourseRoster();
        insertPublishedLabScore();
        insertPublishedHomeworkScore();
    }

    @Test
    void grdGradeEventsCreateLrnNotificationsForPublishChangeAndReviewFlow() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        createGradeItem("作业一", "HWK", 401, "0.60");
        mockMvc.perform(post("/api/v1/courses/101/grades/sync")
                        .headers(teacherHeaders()))
                .andExpect(status().isOk());

        String publishJson = mockMvc.perform(post("/api/v1/courses/101/grades/publish")
                        .headers(teacherHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "publishScope", "PARTIAL_STUDENTS",
                                "studentIds", List.of(601),
                                "gradeItemIds", List.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationStatus").value("SENT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long publishId = objectMapper.readTree(publishJson).at("/data/publishId").asLong();

        mockMvc.perform(get("/api/v1/notifications")
                        .headers(studentHeaders(601L))
                        .param("type", "GRADE")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("成绩已发布"))
                .andExpect(jsonPath("$.data.records[0].sourceModule").value("GRD"))
                .andExpect(jsonPath("$.data.records[0].sourceId").value(publishId));

        long summaryId = courseSummaryId(601L);
        mockMvc.perform(put("/api/v1/course-grade-summaries/{summaryId}/adjust", summaryId)
                        .headers(teacherHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newScore", "88.00",
                                "reason", "联调复核课程总评"
                        ))))
                .andExpect(status().isOk());

        awaitGradeNotifications(studentHeaders(601L), 2, "成绩已变更");
        mockMvc.perform(get("/api/v1/notifications")
                        .headers(studentHeaders(601L))
                        .param("type", "GRADE")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].title").value("成绩已变更"))
                .andExpect(jsonPath("$.data.records[0].sourceModule").value("GRD"))
                .andExpect(jsonPath("$.data.records[0].actionUrl").value("/courses/101?page=grades"));

        String requestJson = mockMvc.perform(post("/api/v1/courses/101/grade-review-requests")
                        .headers(studentHeaders(601L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "FINAL_SCORE",
                                "reason", "总评仍需复核"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long requestId = objectMapper.readTree(requestJson).at("/data/requestId").asLong();

        awaitGradeNotifications(teacherHeaders(), 1, "收到成绩复核申请");
        mockMvc.perform(get("/api/v1/notifications")
                        .headers(teacherHeaders())
                        .param("type", "GRADE")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].title").value("收到成绩复核申请"))
                .andExpect(jsonPath("$.data.records[0].sourceModule").value("GRD"))
                .andExpect(jsonPath("$.data.records[0].sourceId").value(requestId));

        mockMvc.perform(put("/api/v1/grade-review-requests/{requestId}/process", requestId)
                        .headers(teacherHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "REJECT",
                                "responseComment", "已复核，当前成绩无误"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        awaitGradeNotifications(studentHeaders(601L), 3, "成绩复核已处理");
        mockMvc.perform(get("/api/v1/notifications")
                        .headers(studentHeaders(601L))
                        .param("type", "GRADE")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].title").value("成绩复核已处理"))
                .andExpect(jsonPath("$.data.records[0].sourceModule").value("GRD"))
                .andExpect(jsonPath("$.data.records[0].sourceId").value(requestId));
    }

    private long createGradeItem(String name, String sourceType, long sourceId, String weight) throws Exception {
        String responseJson = mockMvc.perform(post("/api/v1/courses/101/grade-items")
                        .headers(teacherHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "sourceType", sourceType,
                                "sourceId", sourceId,
                                "fullScore", "100.00",
                                "weight", weight,
                                "includedInFinal", true,
                                "sortOrder", sourceId == 301 ? 1 : 2
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseJson).at("/data/id").asLong();
    }

    private void awaitGradeNotifications(HttpHeaders headers, int expectedTotal, String expectedTitle)
            throws Exception {
        // GRD 变更/复核类通知由异步 executor 投递，先有界轮询到预期标题，再在后续请求中做完整断言。
        long deadline = System.currentTimeMillis() + 5000;
        String lastBody = "";
        while (System.currentTimeMillis() < deadline) {
            lastBody = mockMvc.perform(get("/api/v1/notifications")
                            .headers(headers)
                            .param("type", "GRADE")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode data = objectMapper.readTree(lastBody).path("data");
            if (data.path("total").asInt() == expectedTotal
                    && expectedTitle.equals(data.at("/records/0/title").asText())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError(
                "grade notification not observed within timeout (total=" + expectedTotal
                        + ", title=" + expectedTitle + "): " + lastBody
        );
    }

    private long courseSummaryId(long studentId) {
        Long summaryId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM t_course_grade_summary
                WHERE course_id = 101
                  AND student_id = ?
                """, Long.class, studentId);
        return summaryId == null ? 0L : summaryId;
    }

    private void insertCourseRoster() {
        jdbcTemplate.update("""
                INSERT INTO crs_course (id, course_name, description, teacher_id, status)
                VALUES (101, 'GRD LRN Integration', 'integration fixture', 501, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO crs_course_member (course_id, user_id, role, join_status, joined_at)
                VALUES
                    (101, 501, 'TEACHER', 'ACTIVE', CURRENT_TIMESTAMP),
                    (101, 601, 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP),
                    (101, 602, 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP),
                    (101, 603, 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP)
                """);
    }

    private void insertPublishedLabScore() {
        jdbcTemplate.update("""
                INSERT INTO lab_experiment (
                    id, course_id, chapter_id, title, description, status, deadline, max_score,
                    attachment_ids, allowed_languages, evaluation_mode, auto_evaluate, report_required,
                    time_limit_ms, memory_limit_kb, created_by, published_at, deleted, created_at, updated_at
                ) VALUES (
                    301, 101, NULL, '实验一', 'GRD source lab', 'SCORE_PUBLISHED', '2026-06-30 23:59:59', 100,
                    NULL, 'python', 'DOCKER_IO', 1, 0, 60000, 262144, 501,
                    '2026-06-01 00:00:00', 0, '2026-06-01 00:00:00', '2026-06-01 00:00:00'
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO lab_submission (
                    id, lab_id, student_id, code_content, file_id, language, submit_status,
                    evaluation_status, final_score, auto_score, version, is_final,
                    submitted_at, created_at, updated_at, deleted
                ) VALUES (
                    30101, 301, 601, 'print(601)', NULL, 'python', 'SUBMITTED',
                    'ACCEPTED', 90, 90, 1, 1,
                    '2026-06-01 00:10:00', '2026-06-01 00:10:00', '2026-06-01 00:20:00', 0
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO lab_score (
                    submission_id, report_id, teacher_id, auto_score, report_score,
                    manual_score, final_score, comment, scored_at, updated_at
                ) VALUES (
                    30101, NULL, 501, 90, NULL, NULL, 90, 'lab graded',
                    '2026-06-01 00:20:00', '2026-06-01 00:20:00'
                )
                """);
    }

    private void insertPublishedHomeworkScore() {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_homework (
                    id, course_id, chapter_id, title, description, type, status, total_score,
                    deadline, allow_resubmit, allow_late_submit, show_evaluation_before_publish,
                    judge_config_id, created_by, published_at, is_deleted, created_at, updated_at
                ) VALUES (
                    401, 101, NULL, '作业一', 'GRD source homework', 'TEXT', 'SCORE_PUBLISHED', 100.00,
                    '2026-06-30 23:59:59', 1, 0, 1, NULL, 501, '2026-06-01 00:00:00',
                    0, '2026-06-01 00:00:00', '2026-06-01 00:00:00'
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO t_hwk_submission (
                    id, homework_id, student_id, submit_type, answer_text, submit_status,
                    evaluation_status, review_status, auto_score, manual_score, final_score,
                    comment, version, is_final, submitted_at, reviewed_by, reviewed_at,
                    created_at, updated_at, is_deleted
                ) VALUES (
                    40101, 401, 601, 'TEXT', 'student 601 answer', 'SUBMITTED',
                    'NONE', 'REVIEWED', NULL, 80.00, 80.00,
                    'graded', 1, 1, '2026-06-01 00:10:00', 501, '2026-06-01 00:20:00',
                    '2026-06-01 00:10:00', '2026-06-01 00:20:00', 0
                )
                """);
    }

    private org.springframework.http.HttpHeaders teacherHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Course-Ids", "101");
        headers.add("X-Manageable-Course-Ids", "101");
        headers.add("X-Course-Student-Ids", "101:601,602,603");
        headers.add("X-Course-Teacher-Ids", "101:501");
        return headers;
    }

    private org.springframework.http.HttpHeaders studentHeaders(long studentId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", Long.toString(studentId));
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Course-Ids", "101");
        headers.add("X-Course-Teacher-Ids", "101:501");
        return headers;
    }
}
