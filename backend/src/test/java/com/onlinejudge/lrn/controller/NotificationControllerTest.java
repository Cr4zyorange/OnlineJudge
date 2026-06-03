package com.onlinejudge.lrn.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_notification_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false",
        "onlinejudge.notifications.internal-token=test-internal-token",
        "spring.sql.init.schema-locations=classpath:schema.sql,file:../database/migrations/20260603_01_create_lrn_notification.sql"
})
@AutoConfigureMockMvc
class NotificationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private SessionUser student;
    private SessionUser otherStudent;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM lrn_notification_status_log");
        jdbcTemplate.update("DELETE FROM lrn_notification");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_chapter");
        jdbcTemplate.update("DELETE FROM crs_course");
        jdbcTemplate.update("DELETE FROM t_auth_audit_log");
        jdbcTemplate.update("DELETE FROM t_auth_session");
        jdbcTemplate.update("DELETE FROM t_auth_user_role");
        jdbcTemplate.update("DELETE FROM t_auth_role_permission");
        jdbcTemplate.update("DELETE FROM t_auth_permission");
        jdbcTemplate.update("DELETE FROM t_auth_role");
        jdbcTemplate.update("DELETE FROM t_auth_user");

        student = registerAndLogin("notice601", "Notice601@pass", "notice601@example.com", "13900003601");
        otherStudent = registerAndLogin("notice602", "Notice602@pass", "notice602@example.com", "13900003602");

        insertCourse(101L, "Java Programming");
        insertCourse(102L, "Database Systems");
        insertMember(101L, student.id());
        insertMember(102L, otherStudent.id());
    }

    @Test
    void internalBusinessEventCreatesCategorizedNotificationsForCourseMembersOnlyAndIsIdempotent() throws Exception {
        Map<String, Object> event = Map.of(
                "idempotencyKey", "hwk-501-published",
                "eventType", "HOMEWORK_PUBLISHED",
                "courseId", 101,
                "sourceModule", "HWK",
                "sourceId", 501,
                "receiverUserIds", List.of(student.id(), otherStudent.id()),
                "title", "新作业发布：Java 编程题",
                "content", "作业截止时间：2026-06-10 23:59",
                "priority", 2,
                "actionUrl", "/courses/101/homeworks/501"
        );

        postEvent(event).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.notificationIds", hasSize(1)));
        Long createLogCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification_status_log
                WHERE user_id = ?
                  AND operation_type = 'CREATE'
                  AND old_status IS NULL
                  AND new_status = 'UNREAD'
                """, Long.class, student.id());
        org.assertj.core.api.Assertions.assertThat(createLogCount).isEqualTo(1);
        postEvent(event).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(0));

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("type", "TASK")
                        .param("isRead", "false")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("新作业发布：Java 编程题"))
                .andExpect(jsonPath("$.data.records[0].type").value("TASK"))
                .andExpect(jsonPath("$.data.records[0].sourceModule").value("HWK"))
                .andExpect(jsonPath("$.data.records[0].sourceId").value(501))
                .andExpect(jsonPath("$.data.records[0].actionUrl").value("/courses/101/homeworks/501"))
                .andExpect(jsonPath("$.data.records[0].isRead").value(false));

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherStudent.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(0)))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void notificationListSupportsTypeReadTimeAndPaginationFiltersForCurrentUserOnly() throws Exception {
        insertNotification(student.id(), 101L, "TASK", "HWK", 501L, "Homework due", false,
                LocalDateTime.of(2026, 6, 2, 9, 0));
        insertNotification(student.id(), 101L, "GRADE", "GRD", 801L, "Grade published", false,
                LocalDateTime.of(2026, 6, 2, 10, 0));
        insertNotification(student.id(), null, "SYSTEM_ANNOUNCEMENT", "SYS", 1L, "Maintenance", true,
                LocalDateTime.of(2026, 6, 1, 8, 0));
        insertNotification(otherStudent.id(), 102L, "GRADE", "GRD", 901L, "Other grade", false,
                LocalDateTime.of(2026, 6, 2, 11, 0));

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("type", "GRADE")
                        .param("isRead", "false")
                        .param("startTime", "2026-06-02T00:00:00")
                        .param("endTime", "2026-06-03T00:00:00")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.records[0].title").value("Grade published"));
    }

    @Test
    void readAndDeleteActionsAreScopedToCurrentUserAndLogged() throws Exception {
        long studentTask = insertNotification(student.id(), 101L, "TASK", "HWK", 501L, "Homework due", false,
                LocalDateTime.of(2026, 6, 2, 9, 0));
        long studentGrade = insertNotification(student.id(), 101L, "GRADE", "GRD", 801L, "Grade published", false,
                LocalDateTime.of(2026, 6, 2, 10, 0));
        long otherGrade = insertNotification(otherStudent.id(), 102L, "GRADE", "GRD", 901L, "Other grade", false,
                LocalDateTime.of(2026, 6, 2, 11, 0));

        mockMvc.perform(put("/api/v1/notifications/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "notificationIds", List.of(studentTask, otherGrade),
                                "readAll", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        org.assertj.core.api.Assertions.assertThat(isRead(studentTask)).isTrue();
        org.assertj.core.api.Assertions.assertThat(isRead(otherGrade)).isFalse();

        mockMvc.perform(put("/api/v1/notifications/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "notificationIds", List.of(),
                                "readAll", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));
        org.assertj.core.api.Assertions.assertThat(isRead(studentGrade)).isTrue();

        mockMvc.perform(delete("/api/v1/notifications/{notificationId}", otherGrade)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-404-04"));

        mockMvc.perform(delete("/api/v1/notifications/{notificationId}", studentTask)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        Long deletedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification
                WHERE id = ?
                  AND deleted_at IS NOT NULL
                """, Long.class, studentTask);
        Long otherDeletedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification
                WHERE id = ?
                  AND deleted_at IS NOT NULL
                """, Long.class, otherGrade);
        Long readLogCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification_status_log
                WHERE user_id = ?
                  AND operation_type = 'MARK_READ'
                  AND old_status = 'UNREAD'
                  AND new_status = 'READ'
                """, Long.class, student.id());
        Long deleteLogCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification_status_log
                WHERE notification_id = ?
                  AND user_id = ?
                  AND operation_type = 'DELETE'
                  AND old_status = 'READ'
                  AND new_status = 'DELETED'
                """, Long.class, studentTask, student.id());

        org.assertj.core.api.Assertions.assertThat(deletedCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(otherDeletedCount).isZero();
        org.assertj.core.api.Assertions.assertThat(readLogCount).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(deleteLogCount).isEqualTo(1);
    }

    @Test
    void notificationEventRequiresInternalTokenAndValidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventType", "GRADE_PUBLISHED",
                                "sourceModule", "GRD",
                                "sourceId", 801,
                                "receiverUserIds", List.of(student.id()),
                                "title", "成绩已发布",
                                "content", "请查看课程成绩"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-403-04"));

        mockMvc.perform(post("/api/v1/notifications/events")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventType", "UNKNOWN",
                                "sourceModule", "GRD",
                                "sourceId", 801,
                                "receiverUserIds", List.of(student.id()),
                                "title", "",
                                "content", "请查看课程成绩"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-400-04"));
    }

    private org.springframework.test.web.servlet.ResultActions postEvent(Map<String, Object> event) throws Exception {
        return mockMvc.perform(post("/api/v1/notifications/events")
                .header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)));
    }

    private void insertCourse(long courseId, String courseName) {
        jdbcTemplate.update("""
                INSERT INTO crs_course (id, course_name, description, teacher_id, status)
                VALUES (?, ?, ?, ?, ?)
                """, courseId, courseName, "course description", 501L, "PUBLISHED");
    }

    private void insertMember(long courseId, long userId) {
        jdbcTemplate.update("""
                INSERT INTO crs_course_member (course_id, user_id, role, join_status, joined_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, courseId, userId, "STUDENT", "ACTIVE");
    }

    private long insertNotification(long userId, Long courseId, String type, String sourceModule, Long sourceId,
                                    String title, boolean read, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO lrn_notification
                    (user_id, course_id, title, content, type, priority, is_read, source_module, source_id, action_url, created_at, read_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, courseId, title, title + " content", type, 1, read, sourceModule, sourceId,
                "/courses/" + (courseId == null ? 0 : courseId), createdAt, read ? createdAt.plusMinutes(5) : null);
        Long id = jdbcTemplate.queryForObject("""
                SELECT id
                FROM lrn_notification
                WHERE user_id = ?
                  AND title = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, userId, title);
        return id == null ? 0 : id;
    }

    private boolean isRead(long notificationId) {
        Boolean read = jdbcTemplate.queryForObject("""
                SELECT is_read
                FROM lrn_notification
                WHERE id = ?
                """, Boolean.class, notificationId);
        return Boolean.TRUE.equals(read);
    }

    private SessionUser registerAndLogin(String username, String password, String email, String phone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password,
                                "userType", "STUDENT",
                                "displayName", username,
                                "email", email,
                                "phone", phone
                        ))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return new SessionUser(
                json.path("data").path("user").path("id").asLong(),
                json.path("data").path("token").asText()
        );
    }

    private record SessionUser(long id, String token) {
    }
}
