package com.onlinejudge.lrn.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_learning_record_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.sql.init.schema-locations=classpath:schema.sql,file:../database/migrations/20260531_01_create_lrn_learning_progress.sql,file:../database/migrations/20260602_01_create_lrn_learning_record.sql"
})
@AutoConfigureMockMvc
class LearningRecordControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ControllableLearningRecordExecutor learningRecordExecutor;

    private SessionUser student;
    private SessionUser otherStudent;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM lrn_learning_record");
        jdbcTemplate.update("DELETE FROM lrn_learning_progress");
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

        student = registerAndLogin("record601", "Record601@pass", "record601@example.com", "13900002601");
        otherStudent = registerAndLogin("record602", "Record602@pass", "record602@example.com", "13900002602");

        insertCourse(101L, "Java Programming");
        insertCourse(102L, "Database Systems");
        insertMember(101L, student.id());
        insertMember(102L, otherStudent.id());
    }

    @Test
    void bearerTokenStudentCanReportBehaviorAndViewSevenDayDashboard() throws Exception {
        insertRecord(student.id(), 101L, "CRS", 701L, "ACCESS", 180,
                LocalDateTime.now().minusDays(1).withHour(9), LocalDateTime.now().minusDays(1).withHour(9).plusMinutes(3));
        insertRecord(student.id(), 101L, "HWK", 501L, "COMPLETE", 0,
                LocalDateTime.now().minusDays(1).withHour(10), LocalDateTime.now().minusDays(1).withHour(10));
        insertRecord(otherStudent.id(), 102L, "CRS", 901L, "ACCESS", 999,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(1).plusMinutes(10));
        insertRecord(student.id(), 101L, "LAB", 301L, "ACCESS", 600,
                LocalDateTime.now().minusDays(8), LocalDateTime.now().minusDays(8).plusMinutes(10));

        mockMvc.perform(post("/api/v1/learning/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", 101,
                                "sourceModule", "LAB",
                                "sourceId", 301,
                                "actionType", "SUBMIT",
                                "durationSeconds", 240,
                                "startedAt", LocalDateTime.now().minusMinutes(4).toString(),
                                "endedAt", LocalDateTime.now().toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(0))
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.sourceModule").value("LAB"))
                .andExpect(jsonPath("$.data.actionType").value("SUBMIT"))
                .andExpect(jsonPath("$.data.durationSeconds").value(240));

        waitUntilRecordCount(student.id(), 101L, "LAB", 301L, "SUBMIT", 1);

        mockMvc.perform(get("/api/v1/learning/statistics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalDurationSeconds").value(420))
                .andExpect(jsonPath("$.data.summary.resourceAccessCount").value(1))
                .andExpect(jsonPath("$.data.summary.completedTaskCount").value(1))
                .andExpect(jsonPath("$.data.summary.submittedTaskCount").value(1))
                .andExpect(jsonPath("$.data.trends", hasSize(7)))
                .andExpect(jsonPath("$.data.trends[5].date").value(LocalDate.now().minusDays(1).toString()))
                .andExpect(jsonPath("$.data.trends[5].durationSeconds").value(180))
                .andExpect(jsonPath("$.data.trends[5].resourceAccessCount").value(1))
                .andExpect(jsonPath("$.data.trends[5].completedTaskCount").value(1))
                .andExpect(jsonPath("$.data.trends[6].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.trends[6].durationSeconds").value(240))
                .andExpect(jsonPath("$.data.recentRecords", hasSize(3)));
    }

    @Test
    void nonMemberCannotReportOrQueryCourseBehavior() throws Exception {
        mockMvc.perform(post("/api/v1/learning/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherStudent.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", 101,
                                "sourceModule", "CRS",
                                "sourceId", 701,
                                "actionType", "ACCESS",
                                "durationSeconds", 60
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));

        mockMvc.perform(get("/api/v1/learning/statistics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherStudent.token())
                        .param("courseId", "101"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));
    }

    @Test
    void invalidLearningRecordPayloadIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/learning/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", 101,
                                "sourceModule", "LAB",
                                "sourceId", 301,
                                "actionType", "ACCESS",
                                "durationSeconds", -1
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-400-03"));
    }

    @Test
    void learningRecordReportsAreRateLimitedPerUserAndSource() throws Exception {
        for (int index = 0; index < 10; index += 1) {
            insertRecord(student.id(), 101L, "CRS", 701L, "ACCESS", 5,
                    LocalDateTime.now().minusSeconds(50 - index),
                    LocalDateTime.now().minusSeconds(49 - index));
        }

        mockMvc.perform(post("/api/v1/learning/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", 101,
                                "sourceModule", "CRS",
                                "sourceId", 701,
                                "actionType", "ACCESS",
                                "durationSeconds", 5
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LRN-429-03"));
    }

    @Test
    void sameResourceReportsAreRateLimitedBeforeAsynchronousWritesComplete() throws Exception {
        // 保持异步写入挂起，确定性地证明：即使写入尚未落库，内存中的在途请求也会计入限流。
        // 生产 executor 是真实线程池，写入可能在下一次请求前完成，导致第 10 次请求被误判 429（时序敏感）。
        learningRecordExecutor.holdTasks(true);
        try {
            for (int index = 0; index < 10; index += 1) {
                mockMvc.perform(post("/api/v1/learning/records")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "courseId", 101,
                                        "sourceModule", "LAB",
                                        "sourceId", 301,
                                        "actionType", "STUDY",
                                        "durationSeconds", 5
                                ))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.id").value(0));
            }

            mockMvc.perform(post("/api/v1/learning/records")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "courseId", 101,
                                    "sourceModule", "LAB",
                                    "sourceId", 301,
                                    "actionType", "STUDY",
                                    "durationSeconds", 5
                            ))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("LRN-429-03"));
        } finally {
            learningRecordExecutor.holdTasks(false);
        }
    }

    @Test
    void learningRecordRateLimitUsesServerReceiveTimeInsteadOfClientStartedAt() throws Exception {
        for (int index = 0; index < 10; index += 1) {
            insertRecordWithCreatedAt(student.id(), 101L, "CRS", 701L, "ACCESS", 5,
                    LocalDateTime.now().minusHours(2).minusSeconds(index),
                    LocalDateTime.now().minusHours(2).minusSeconds(index - 1),
                    LocalDateTime.now().minusSeconds(30 - index));
        }

        mockMvc.perform(post("/api/v1/learning/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", 101,
                                "sourceModule", "CRS",
                                "sourceId", 701,
                                "actionType", "ACCESS",
                                "durationSeconds", 5,
                                "startedAt", LocalDateTime.now().minusHours(1).toString(),
                                "endedAt", LocalDateTime.now().minusHours(1).plusSeconds(5).toString()
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LRN-429-03"));
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

    private void insertRecord(long userId, long courseId, String sourceModule, long sourceId, String actionType,
                              int durationSeconds, LocalDateTime startedAt, LocalDateTime endedAt) {
        jdbcTemplate.update("""
                INSERT INTO lrn_learning_record
                    (user_id, course_id, source_module, source_id, action_type, duration, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, courseId, sourceModule, sourceId, actionType, durationSeconds, startedAt, endedAt);
    }

    private void insertRecordWithCreatedAt(long userId, long courseId, String sourceModule, long sourceId, String actionType,
                                           int durationSeconds, LocalDateTime startedAt, LocalDateTime endedAt,
                                           LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO lrn_learning_record
                    (user_id, course_id, source_module, source_id, action_type, duration, started_at, ended_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, courseId, sourceModule, sourceId, actionType, durationSeconds, startedAt, endedAt, createdAt);
    }

    private void waitUntilRecordCount(long userId, long courseId, String sourceModule, long sourceId, String actionType,
                                      int expectedCount) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM lrn_learning_record
                    WHERE user_id = ?
                      AND course_id = ?
                      AND source_module = ?
                      AND source_id = ?
                      AND action_type = ?
                    """, Integer.class, userId, courseId, sourceModule, sourceId, actionType);
            if (count != null && count >= expectedCount) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("learning record was not written asynchronously before timeout");
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

    @TestConfiguration
    static class ControllableExecutorConfig {
        @Bean(name = "learningRecordExecutor")
        ControllableLearningRecordExecutor learningRecordExecutor() {
            return new ControllableLearningRecordExecutor();
        }
    }

    static class ControllableLearningRecordExecutor implements Executor {
        private final Deque<Runnable> held = new ArrayDeque<>();
        private boolean holding;

        @Override
        public void execute(Runnable command) {
            if (holding) {
                held.addLast(command);
                return;
            }
            command.run();
        }

        void holdTasks(boolean holding) {
            this.holding = holding;
            if (!holding) {
                Runnable task;
                while ((task = held.pollFirst()) != null) {
                    task.run();
                }
            }
        }
    }
}
