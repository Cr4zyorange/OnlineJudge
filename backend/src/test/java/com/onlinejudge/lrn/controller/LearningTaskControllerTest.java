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
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_learning_task_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false",
        "spring.sql.init.schema-locations=classpath:schema.sql,classpath:lrn_task_source_fixture.sql,file:../database/migrations/20260525_02_create_lab_experiment.sql,file:../database/migrations/20260530_01_create_lrn_learning_task.sql"
})
@AutoConfigureMockMvc
class LearningTaskControllerTest {
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
        jdbcTemplate.update("DELETE FROM lrn_learning_task");
        jdbcTemplate.update("DELETE FROM lab_testcase");
        jdbcTemplate.update("DELETE FROM lab_experiment");
        jdbcTemplate.update("DELETE FROM t_hwk_homework");
        jdbcTemplate.update("DELETE FROM crs_resource");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_course");
        jdbcTemplate.update("DELETE FROM t_auth_audit_log");
        jdbcTemplate.update("DELETE FROM t_auth_session");
        jdbcTemplate.update("DELETE FROM t_auth_user_role");
        jdbcTemplate.update("DELETE FROM t_auth_role_permission");
        jdbcTemplate.update("DELETE FROM t_auth_permission");
        jdbcTemplate.update("DELETE FROM t_auth_role");
        jdbcTemplate.update("DELETE FROM t_auth_user");

        student = registerAndLogin("student601", "Student601@pass", "student601@example.com", "13900000601");
        otherStudent = registerAndLogin("student602", "Student602@pass", "student602@example.com", "13900000602");

        insertCourse(101L, "Java Programming");
        insertCourse(102L, "Database Systems");
        insertMember(101L, student.id());
        insertMember(102L, otherStudent.id());

        insertResource(701L, 101L, "Chapter 1 Slides", 501L);
        insertLab(301L, 101L, "Linked List Lab", LocalDateTime.now().minusDays(1), "PUBLISHED");
        insertHomework(501L, 101L, "Java Homework 1", LocalDateTime.now().plusDays(2), "PUBLISHED");
        insertTask(student.id(), 101L, "HWK", 502L, "HOMEWORK", "Tracked Homework Progress",
                LocalDateTime.now().plusDays(3), 25, "IN_PROGRESS", "/courses/101/homeworks/502");

        insertResource(702L, 102L, "Private Database Slides", 502L);
        insertLab(302L, 102L, "Database Lab", LocalDateTime.now().plusDays(1), "PUBLISHED");
        insertHomework(503L, 102L, "Database Homework", LocalDateTime.now().plusDays(4), "PUBLISHED");
        insertTask(otherStudent.id(), 101L, "LAB", 303L, "EXPERIMENT", "Another Student Task",
                LocalDateTime.now().plusDays(1), 0, "NOT_STARTED", "/courses/101/labs/303");
    }

    @Test
    void studentGetsAggregatedResourceLabHomeworkAndOwnSnapshotsForMemberCourses() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records", hasSize(4)))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.records[*].taskType",
                        containsInAnyOrder("RESOURCE", "EXPERIMENT", "HOMEWORK", "HOMEWORK")))
                .andExpect(jsonPath("$.data.records[?(@.title == 'Chapter 1 Slides')].actionUrl",
                        hasItem("/courses/101")))
                .andExpect(jsonPath("$.data.records[0].title").value("Linked List Lab"))
                .andExpect(jsonPath("$.data.records[0].status").value("OVERDUE"));
    }

    @Test
    void pageAndSizeReturnTheRequestedSliceOfAggregatedTasks() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                .andExpect(jsonPath("$.data.records[0].title").value("Tracked Homework Progress"))
                .andExpect(jsonPath("$.data.records[1].title").value("Chapter 1 Slides"));
    }

    @Test
    void studentCanFilterByTypeStatusCourseAndSortByDeadlineDescending() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("taskType", "HOMEWORK,EXPERIMENT")
                        .param("status", "IN_PROGRESS")
                        .param("courseId", "101")
                        .param("sortBy", "deadline")
                        .param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].title").value("Tracked Homework Progress"))
                .andExpect(jsonPath("$.data.records[0].actionUrl").value("/courses/101/homeworks/502"));
    }

    @Test
    void bearerTokenStudentCannotSeeTasksFromCoursesWhereTheyAreNotMembers() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherStudent.token())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(3)))
                .andExpect(jsonPath("$.data.records[*].title",
                        containsInAnyOrder("Private Database Slides", "Database Lab", "Database Homework")));
    }

    @Test
    void unauthenticatedTaskListRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
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

    private void insertResource(long resourceId, long courseId, String name, long uploaderId) {
        jdbcTemplate.update("""
                INSERT INTO crs_resource
                    (id, course_id, resource_name, resource_type, visibility, storage_key,
                     original_filename, content_type, file_size, upload_user_id, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, resourceId, courseId, name, "COURSEWARE", "STUDENT",
                "resources/" + resourceId + ".pdf", name + ".pdf", "application/pdf",
                1024L, uploaderId, false);
    }

    private void insertLab(long labId, long courseId, String title, LocalDateTime deadline, String status) {
        jdbcTemplate.update("""
                INSERT INTO lab_experiment
                    (id, course_id, title, description, status, deadline, max_score, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, labId, courseId, title, "lab description", status, deadline, 100, 501L);
    }

    private void insertHomework(long homeworkId, long courseId, String title, LocalDateTime deadline, String status) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_homework
                    (id, course_id, title, description, type, status, total_score, deadline, created_by, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, homeworkId, courseId, title, "homework description", "FILE", status, 100, deadline, 501L);
    }

    private void insertTask(
            long userId,
            long courseId,
            String sourceModule,
            long sourceId,
            String taskType,
            String title,
            LocalDateTime deadline,
            int progress,
            String status,
            String actionUrl
    ) {
        jdbcTemplate.update("""
                INSERT INTO lrn_learning_task
                    (user_id, course_id, source_module, source_id, task_type, title, deadline, progress, status, action_url, snapshot_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, userId, courseId, sourceModule, sourceId, taskType, title, deadline, progress, status, actionUrl);
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
