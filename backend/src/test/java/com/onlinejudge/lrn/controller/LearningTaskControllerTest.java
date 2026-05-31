package com.onlinejudge.lrn.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_learning_task_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:schema.sql,file:../database/migrations/20260530_01_create_lrn_learning_task.sql"
})
@AutoConfigureMockMvc
class LearningTaskControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM lrn_learning_task");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_course");

        insertCourse(101L, "Java程序设计");
        insertCourse(102L, "数据库系统");
        insertMember(101L, 601L);

        insertTask(601L, 101L, "HWK", 501L, "HOMEWORK", "Java作业1",
                LocalDateTime.now().plusDays(2), 25, "IN_PROGRESS", "/courses/101/homeworks/501");
        insertTask(601L, 101L, "LAB", 301L, "EXPERIMENT", "链表实验",
                LocalDateTime.now().minusDays(1), 0, "NOT_STARTED", "/courses/101/labs/301");
        insertTask(602L, 101L, "HWK", 502L, "HOMEWORK", "他人的作业",
                LocalDateTime.now().plusDays(1), 0, "NOT_STARTED", "/courses/101/homeworks/502");
        insertTask(601L, 102L, "CRS", 701L, "RESOURCE", "非成员课程资源",
                null, 0, "NOT_STARTED", "/courses/102/resources/701");
    }

    @Test
    void studentGetsOnlyOwnCourseMemberTasksWithDocumentedPayload() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks")
                        .headers(studentHeaders("101"))
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.records[0].taskType").value("EXPERIMENT"))
                .andExpect(jsonPath("$.data.records[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$.data.records[0].courseName").value("Java程序设计"))
                .andExpect(jsonPath("$.data.records[1].taskType").value("HOMEWORK"))
                .andExpect(jsonPath("$.data.records[1].progress").value(25));
    }

    @Test
    void studentCanFilterByTypeStatusCourseAndSortByDeadlineDescending() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks")
                        .headers(studentHeaders("101"))
                        .param("taskType", "HOMEWORK,EXPERIMENT")
                        .param("status", "IN_PROGRESS")
                        .param("courseId", "101")
                        .param("sortBy", "deadline")
                        .param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].title").value("Java作业1"))
                .andExpect(jsonPath("$.data.records[0].actionUrl").value("/courses/101/homeworks/501"));
    }

    @Test
    void unauthenticatedTaskListRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-01"));
    }

    private void insertCourse(long courseId, String courseName) {
        jdbcTemplate.update("""
                INSERT INTO crs_course (id, course_name, description, teacher_id, status)
                VALUES (?, ?, ?, ?, ?)
                """, courseId, courseName, "课程说明", 501L, "PUBLISHED");
    }

    private void insertMember(long courseId, long userId) {
        jdbcTemplate.update("""
                INSERT INTO crs_course_member (course_id, user_id, role, join_status, joined_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, courseId, userId, "STUDENT", "ACTIVE");
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

    private org.springframework.http.HttpHeaders studentHeaders(String courseIds) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "601");
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Course-Ids", courseIds);
        return headers;
    }
}
