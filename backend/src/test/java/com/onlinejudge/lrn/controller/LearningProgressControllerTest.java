package com.onlinejudge.lrn.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.controller.RegisterRequest;
import com.onlinejudge.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lrn_learning_progress_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false",
        "spring.sql.init.schema-locations=classpath:schema.sql,file:../database/migrations/20260531_01_create_lrn_learning_progress.sql"
})
@AutoConfigureMockMvc
class LearningProgressControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    private SessionUser student;
    private SessionUser otherStudent;
    private SessionUser teacher;

    @BeforeEach
    void setUp() throws Exception {
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

        student = registerAndLogin("progress601", "Student601@pass", "progress601@example.com", "13900001601");
        otherStudent = registerAndLogin("progress602", "Student602@pass", "progress602@example.com", "13900001602");
        teacher = registerTrustedAndLogin("progressTeacher", "Teacher601@pass", "TEACHER", "progress-teacher@example.com");

        insertCourse(101L, "Java Programming");
        insertCourse(102L, "Database Systems");
        insertChapter(1001L, 101L, "Variables");
        insertChapter(1002L, 101L, "Collections");
        insertChapter(2001L, 102L, "Indexes");
        insertMember(101L, student.id());
        insertTeacherMember(101L, teacher.id());
        insertMember(102L, otherStudent.id());
    }

    @Test
    void bearerTokenStudentCanSaveAndResumeOwnCourseProgress() throws Exception {
        mockMvc.perform(post("/api/v1/learning/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", 101,
                                "chapterId", 1001,
                                "sourceModule", "CRS",
                                "sourceId", 701,
                                "progressPercent", 65,
                                "lastPosition", "video_play_time=1234"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.chapterId").value(1001))
                .andExpect(jsonPath("$.data.progressPercent").value(65))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.lastPosition").value("video_play_time=1234"));

        mockMvc.perform(get("/api/v1/learning/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("courseId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses", hasSize(1)))
                .andExpect(jsonPath("$.data.courses[0].courseId").value(101))
                .andExpect(jsonPath("$.data.courses[0].progressPercent").value(65))
                .andExpect(jsonPath("$.data.courses[0].continueLearning.lastPosition").value("video_play_time=1234"))
                .andExpect(jsonPath("$.data.courses[0].chapters", hasSize(1)))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].chapterName").value("Variables"))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].progressPercent").value(65));
    }

    @Test
    void savingSameSourceProgressUpdatesBreakpointInsteadOfDuplicatingRows() throws Exception {
        saveProgress(student.token(), 101, 1001, "LAB", 301, 40, "step=compile");
        saveProgress(student.token(), 101, 1001, "LAB", 301, 100, "step=report-submitted");

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lrn_learning_progress
                WHERE user_id = ? AND course_id = ? AND source_module = ? AND source_id = ?
                """, Integer.class, student.id(), 101L, "LAB", 301L);

        mockMvc.perform(get("/api/v1/learning/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("courseId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[0].progressPercent").value(100))
                .andExpect(jsonPath("$.data.courses[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.courses[0].continueLearning.lastPosition").value("step=report-submitted"));

        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void courseProgressAggregatesChapterProgressByAveragePercent() throws Exception {
        saveProgress(student.token(), 101, 1001, "CRS", 701, 80, "slide=8");
        saveProgress(student.token(), 101, 1002, "HWK", 501, 20, "question=2");

        mockMvc.perform(get("/api/v1/learning/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("courseId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[0].progressPercent").value(50))
                .andExpect(jsonPath("$.data.courses[0].chapters", hasSize(2)))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].progressPercent").value(80))
                .andExpect(jsonPath("$.data.courses[0].chapters[1].progressPercent").value(20));
    }

    @Test
    void nonMemberCannotSaveOrQueryCourseProgress() throws Exception {
        mockMvc.perform(post("/api/v1/learning/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherStudent.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", 101,
                                "chapterId", 1001,
                                "sourceModule", "CRS",
                                "sourceId", 701,
                                "progressPercent", 10,
                                "lastPosition", "slide=1"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));

        mockMvc.perform(get("/api/v1/learning/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherStudent.token())
                        .param("courseId", "101"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));
    }

    @Test
    void invalidProgressPayloadIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/learning/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", 101,
                                "sourceModule", "CRS",
                                "sourceId", 701,
                                "progressPercent", 101,
                                "lastPosition", "slide=1"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-400-02"));
    }

    @Test
    void unauthenticatedProgressRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/learning/progress"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
    }

    @Test
    void teacherCanViewAggregateProgressOnlyForManagedCourse() throws Exception {
        saveProgress(student.token(), 101, 1001, "CRS", 701, 80, "resourceId=701");
        saveProgress(student.token(), 101, 1002, "LAB", 301, 20, "code=print");

        mockMvc.perform(get("/api/v1/learning/progress/teacher")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher.token())
                        .param("courseId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.courseName").value("Java Programming"))
                .andExpect(jsonPath("$.data.studentCount").value(1))
                .andExpect(jsonPath("$.data.averageProgressPercent").value(50))
                .andExpect(jsonPath("$.data.students", hasSize(1)))
                .andExpect(jsonPath("$.data.students[0].studentId").value(student.id()))
                .andExpect(jsonPath("$.data.students[0].progressPercent").value(50));

        mockMvc.perform(get("/api/v1/learning/progress/teacher")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher.token())
                        .param("courseId", "102"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));
    }

    @Test
    void studentCannotViewTeacherAggregateProgress() throws Exception {
        mockMvc.perform(get("/api/v1/learning/progress/teacher")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token())
                        .param("courseId", "101"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));
    }

    private void saveProgress(
            String token,
            long courseId,
            long chapterId,
            String sourceModule,
            long sourceId,
            int progressPercent,
            String lastPosition
    ) throws Exception {
        mockMvc.perform(post("/api/v1/learning/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", courseId,
                                "chapterId", chapterId,
                                "sourceModule", sourceModule,
                                "sourceId", sourceId,
                                "progressPercent", progressPercent,
                                "lastPosition", lastPosition
                        ))))
                .andExpect(status().isOk());
    }

    private void insertCourse(long courseId, String courseName) {
        jdbcTemplate.update("""
                INSERT INTO crs_course (id, course_name, description, teacher_id, status)
                VALUES (?, ?, ?, ?, ?)
                """, courseId, courseName, "course description", 501L, "PUBLISHED");
    }

    private void insertChapter(long chapterId, long courseId, String chapterName) {
        jdbcTemplate.update("""
                INSERT INTO crs_chapter (id, course_id, chapter_name, sort_order, visible_status, chapter_type)
                VALUES (?, ?, ?, ?, ?, ?)
                """, chapterId, courseId, chapterName, 1, 1, 1);
    }

    private void insertMember(long courseId, long userId) {
        jdbcTemplate.update("""
                INSERT INTO crs_course_member (course_id, user_id, role, join_status, joined_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, courseId, userId, "STUDENT", "ACTIVE");
    }

    private void insertTeacherMember(long courseId, long userId) {
        jdbcTemplate.update("""
                INSERT INTO crs_course_member (course_id, user_id, role, join_status, joined_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, courseId, userId, "TEACHER", "ACTIVE");
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

    private SessionUser registerTrustedAndLogin(String username, String password, String userType, String email) throws Exception {
        authService.registerTrusted(new RegisterRequest(
                username,
                password,
                userType,
                username,
                null,
                email,
                null
        ), userType);

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
