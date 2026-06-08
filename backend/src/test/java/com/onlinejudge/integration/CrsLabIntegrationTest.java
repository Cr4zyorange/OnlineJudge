package com.onlinejudge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.controller.RegisterRequest;
import com.onlinejudge.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static java.util.Map.entry;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crs_lab_integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
                "DELETE FROM lab_score_change_log",
                "DELETE FROM lab_score",
                "DELETE FROM lab_report",
                "DELETE FROM lab_evaluation_result",
                "DELETE FROM lab_evaluation",
                "DELETE FROM lab_submission",
                "DELETE FROM lab_testcase",
                "DELETE FROM lab_experiment",
                "DELETE FROM crs_announcement",
                "DELETE FROM crs_resource",
                "DELETE FROM crs_chapter",
                "DELETE FROM crs_course_member",
                "DELETE FROM crs_course",
                "DELETE FROM t_auth_audit_log",
                "DELETE FROM t_auth_session",
                "DELETE FROM t_auth_user_role",
                "DELETE FROM t_auth_role_permission",
                "DELETE FROM t_auth_permission",
                "DELETE FROM t_auth_role",
                "DELETE FROM t_auth_user"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class CrsLabIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Test
    void crsMembershipDrivesLabCreationSubmissionFilteringAndTeacherReview() throws Exception {
        long teacherId = seedUser("int91-teacher", "Teacher91@pass", "TEACHER");
        long studentId = seedUser("int91-student", "Student91@pass", "STUDENT");
        long unsubmittedStudentId = seedUser("int91-unsubmitted", "Student92@pass", "STUDENT");
        long outsiderId = seedUser("int91-outsider", "Student93@pass", "STUDENT");
        String teacherToken = loginToken("int91-teacher", "Teacher91@pass");
        String studentToken = loginToken("int91-student", "Student91@pass");
        String unsubmittedStudentToken = loginToken("int91-unsubmitted", "Student92@pass");
        String outsiderToken = loginToken("int91-outsider", "Student93@pass");

        long courseId = createCourse(teacherToken);
        joinCourse(courseId, studentToken);
        joinCourse(courseId, unsubmittedStudentToken);

        String createdLab = mockMvc.perform(post("/api/v1/courses/" + courseId + "/labs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                entry("title", "INT-02 CRS 与 LAB 联调实验"),
                                entry("description", "验证课程成员关系驱动实验提交与教师查看"),
                                entry("deadline", LocalDateTime.now().plusDays(7).withNano(0)),
                                entry("maxScore", 100),
                                entry("allowedLanguages", "java,python"),
                                entry("evaluationMode", "MANUAL"),
                                entry("autoEvaluate", false),
                                entry("reportRequired", false),
                                entry("timeLimitMs", 1000),
                                entry("memoryLimitKb", 65536),
                                entry("testcases", java.util.List.of())
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseId", is((int) courseId)))
                .andExpect(jsonPath("$.data.createdBy", is((int) teacherId)))
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long labId = objectMapper.readTree(createdLab).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/labs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));

        mockMvc.perform(post("/api/v1/labs/" + labId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PUBLISHED")));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/labs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].title", is("INT-02 CRS 与 LAB 联调实验")));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/labs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("LAB-403-01")));

        mockMvc.perform(multipart("/api/v1/labs/" + labId + "/submissions")
                        .file(new MockMultipartFile("file", "Main.java", "text/plain", "class Main {}".getBytes()))
                        .param("language", "java")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("无课程访问权限")));

        String submitted = mockMvc.perform(multipart("/api/v1/labs/" + labId + "/submissions")
                        .file(new MockMultipartFile("file", "Main.java", "text/plain", "class Main {}".getBytes()))
                        .param("language", "java")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.labId", is((int) labId)))
                .andExpect(jsonPath("$.data.studentId", is((int) studentId)))
                .andExpect(jsonPath("$.data.evaluationStatus", is("NONE")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long submissionId = objectMapper.readTree(submitted).path("data").path("submissionId").asLong();

        mockMvc.perform(get("/api/v1/labs/" + labId + "/submissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].submissionId", is((int) submissionId)));

        mockMvc.perform(get("/api/v1/labs/" + labId + "/submissions?studentId=" + outsiderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("学生只能查看本人提交")));

        mockMvc.perform(get("/api/v1/labs/" + labId + "/submissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].studentId", is((int) studentId)));

        mockMvc.perform(get("/api/v1/labs/" + labId + "/submissions/" + submissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(unsubmittedStudentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("无权限查看他人提交")));

        mockMvc.perform(get("/api/v1/labs/" + labId + "/statistics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudentCount", is(2)))
                .andExpect(jsonPath("$.data.submittedCount", is(1)))
                .andExpect(jsonPath("$.data.unsubmittedCount", is(1)))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds", contains((int) unsubmittedStudentId)))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds", not(contains((int) outsiderId))));
    }

    private long createCourse(String teacherToken) throws Exception {
        String createdCourse = mockMvc.perform(post("/api/v1/courses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "INT-02 CRS LAB 联调课程",
                                "description", "CRS real course membership drives LAB",
                                "enrollmentMode", "PUBLIC",
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(createdCourse).path("data").path("id").asLong();
    }

    private void joinCourse(long courseId, String studentToken) throws Exception {
        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    private long seedUser(String username, String password, String userType) {
        return authService.registerTrusted(new RegisterRequest(
                username,
                password,
                userType,
                username,
                null,
                username + "@example.com",
                null
        ), userType).id();
    }

    private String loginToken(String account, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", account,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        return root.path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
