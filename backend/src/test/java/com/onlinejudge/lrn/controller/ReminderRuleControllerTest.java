package com.onlinejudge.lrn.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.lrn.service.ReminderRuleService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_reminder_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false",
        "onlinejudge.lrn.reminders.scheduling-enabled=false",
        "spring.sql.init.schema-locations=classpath:schema.sql,file:../database/migrations/20260525_02_create_lab_experiment.sql,file:../database/migrations/20260526_01_create_lab_submission.sql,file:../database/migrations/20260530_01_create_hwk_homework.sql,file:../database/migrations/20260601_01_create_hwk_submission.sql,file:../database/migrations/20260603_01_create_lrn_notification.sql,file:../database/migrations/20260605_01_create_lrn_reminder_rule.sql"
})
@AutoConfigureMockMvc
class ReminderRuleControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReminderRuleService reminderRuleService;

    private SessionUser student;
    private SessionUser otherStudent;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM lrn_reminder_scan_log");
        jdbcTemplate.update("DELETE FROM lrn_notification_status_log");
        jdbcTemplate.update("DELETE FROM lrn_notification");
        jdbcTemplate.update("DELETE FROM lrn_reminder_rule");
        jdbcTemplate.update("DELETE FROM lrn_notification_setting");
        jdbcTemplate.update("DELETE FROM t_hwk_submission");
        jdbcTemplate.update("DELETE FROM t_hwk_test_case");
        jdbcTemplate.update("DELETE FROM t_hwk_question");
        jdbcTemplate.update("DELETE FROM t_hwk_judge_config");
        jdbcTemplate.update("DELETE FROM t_hwk_homework");
        jdbcTemplate.update("DELETE FROM lab_submission");
        jdbcTemplate.update("DELETE FROM lab_testcase");
        jdbcTemplate.update("DELETE FROM lab_experiment");
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

        student = registerAndLogin("remind601", "Remind601@pass", "remind601@example.com", "13900004601");
        otherStudent = registerAndLogin("remind602", "Remind602@pass", "remind602@example.com", "13900004602");
        insertCourse(201L, "Deadline Course");
        insertMember(201L, student.id());
        insertMember(201L, otherStudent.id());
    }

    @Test
    void currentUserCanReadAndSaveReminderRulesAndNotificationSettings() throws Exception {
        mockMvc.perform(get("/api/v1/reminder-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules", hasSize(4)))
                .andExpect(jsonPath("$.data.settings.enableExperiment").value(true))
                .andExpect(jsonPath("$.data.settings.enableHomework").value(true))
                .andExpect(jsonPath("$.data.settings.enableNonCriticalReminder").value(true));

        Map<String, Object> payload = Map.of(
                "rules", List.of(
                        Map.of("reminderType", "HOMEWORK_DEADLINE", "sourceModule", "HWK", "aheadMinutes", 1440, "enabled", true),
                        Map.of("reminderType", "HOMEWORK_DEADLINE", "sourceModule", "HWK", "aheadMinutes", 60, "enabled", false),
                        Map.of("reminderType", "EXPERIMENT_DEADLINE", "sourceModule", "LAB", "aheadMinutes", 1440, "enabled", true),
                        Map.of("reminderType", "EXPERIMENT_DEADLINE", "sourceModule", "LAB", "aheadMinutes", 60, "enabled", true)
                ),
                "settings", Map.of(
                        "enableExperiment", true,
                        "enableHomework", false,
                        "enableGrade", true,
                        "enableAnnouncement", true,
                        "enableNonCriticalReminder", false
                )
        );

        mockMvc.perform(put("/api/v1/reminder-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules[?(@.reminderType=='HOMEWORK_DEADLINE' && @.aheadMinutes==60)].enabled").value(false))
                .andExpect(jsonPath("$.data.settings.enableHomework").value(false))
                .andExpect(jsonPath("$.data.settings.enableNonCriticalReminder").value(false));

        mockMvc.perform(get("/api/v1/reminder-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherStudent.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.enableHomework").value(true))
                .andExpect(jsonPath("$.data.settings.enableNonCriticalReminder").value(true));
    }

    @Test
    void savingReminderRulesRejectsInvalidAheadMinutesAndSourceContract() throws Exception {
        Map<String, Object> payload = Map.of(
                "rules", List.of(Map.of(
                        "reminderType", "HOMEWORK_DEADLINE",
                        "sourceModule", "LAB",
                        "aheadMinutes", -1,
                        "enabled", true
                )),
                "settings", Map.of(
                        "enableExperiment", true,
                        "enableHomework", true,
                        "enableGrade", true,
                        "enableAnnouncement", true,
                        "enableNonCriticalReminder", true
                )
        );

        mockMvc.perform(put("/api/v1/reminder-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-400-06"));
    }

    @Test
    void deadlineScanCreatesRemindersForUnsubmittedStudentsAndHonorsPreferences() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 5, 9, 0);
        insertHomework(701L, 201L, "Chapter homework", now.plusHours(23));
        insertLab(801L, 201L, "Docker lab", now.plusMinutes(55));
        insertHomeworkSubmission(701L, otherStudent.id());
        disableNonCriticalReminder(otherStudent.id());

        int created = reminderRuleService.scanDeadlineReminders(now);

        assertThat(created).isEqualTo(2);
        assertThat(countNotification(student.id(), "HWK", 701L)).isEqualTo(1);
        assertThat(countNotification(student.id(), "LAB", 801L)).isEqualTo(1);
        assertThat(countNotification(otherStudent.id(), "HWK", 701L)).isZero();
        assertThat(countNotification(otherStudent.id(), "LAB", 801L)).isZero();

        int duplicateCreated = reminderRuleService.scanDeadlineReminders(now.plusMinutes(5));
        Long scanLogCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lrn_reminder_scan_log", Long.class);

        assertThat(duplicateCreated).isZero();
        assertThat(scanLogCount).isEqualTo(2);
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

    private void insertHomework(long homeworkId, long courseId, String title, LocalDateTime deadline) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_homework
                    (id, course_id, title, description, type, status, total_score, deadline, created_by, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, homeworkId, courseId, title, "homework description", "PROGRAMMING", "PUBLISHED",
                100, deadline, 501L, deadline.minusDays(7));
    }

    private void insertLab(long labId, long courseId, String title, LocalDateTime deadline) {
        jdbcTemplate.update("""
                INSERT INTO lab_experiment
                    (id, course_id, title, description, status, deadline, max_score, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, labId, courseId, title, "lab description", "PUBLISHED", deadline, 100, 501L);
    }

    private void insertHomeworkSubmission(long homeworkId, long userId) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_submission
                    (homework_id, student_id, submit_type, submit_status, evaluation_status, version, is_final)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, homeworkId, userId, "TEXT", "SUBMITTED", "NONE", 1, true);
    }

    private void disableNonCriticalReminder(long userId) {
        jdbcTemplate.update("""
                INSERT INTO lrn_notification_setting
                    (user_id, enable_experiment, enable_homework, enable_grade, enable_announcement, enable_non_critical_reminder)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, true, true, true, true, false);
    }

    private long countNotification(long userId, String sourceModule, long sourceId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM lrn_notification
                WHERE user_id = ?
                  AND type = 'LEARNING_REMINDER'
                  AND source_module = ?
                  AND source_id = ?
                  AND deleted_at IS NULL
                """, Long.class, userId, sourceModule, sourceId);
        return count == null ? 0 : count;
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
