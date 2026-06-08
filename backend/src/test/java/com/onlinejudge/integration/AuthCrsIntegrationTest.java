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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_crs_integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
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
class AuthCrsIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Test
    void loggedInTeacherCreatesCourseStudentJoinsAndNonMemberIsRejected() throws Exception {
        long teacherId = seedUser("int90-teacher", "Teacher90@pass", "TEACHER");
        seedUser("int90-student", "Student90@pass", "STUDENT");
        seedUser("int90-outsider", "Student91@pass", "STUDENT");
        String teacherToken = loginToken("int90-teacher", "Teacher90@pass");
        String studentToken = loginToken("int90-student", "Student90@pass");
        String outsiderToken = loginToken("int90-outsider", "Student91@pass");

        String createdCourse = mockMvc.perform(post("/api/v1/courses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "INT-01 真实登录联调课程",
                                "description", "AUTH token drives CRS current user",
                                "enrollmentMode", "PUBLIC",
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("INT-01 真实登录联调课程")))
                .andExpect(jsonPath("$.data.teacherId", is((int) teacherId)))
                .andExpect(jsonPath("$.data.manageable", is(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long courseId = objectMapper.readTree(createdCourse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ERR-AUTH-05")))
                .andExpect(jsonPath("$.message", is("无权限访问")));

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(true)))
                .andExpect(jsonPath("$.data.teacher", is(false)))
                .andExpect(jsonPath("$.data.role", is("STUDENT")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("INT-01 真实登录联调课程")))
                .andExpect(jsonPath("$.data.member", is(true)))
                .andExpect(jsonPath("$.data.manageable", is(false)))
                .andExpect(jsonPath("$.data.inviteCode").doesNotExist());

        mockMvc.perform(get("/api/v1/courses?scope=mine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.list[0].name", is("INT-01 真实登录联调课程")))
                .andExpect(jsonPath("$.data.list[0].teacherId", is((int) teacherId)));
    }

    @Test
    void crsRejectsHeaderOnlyIdentityWhenAuthIntegrationIsRequired() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .header("X-User-Id", "901")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("ERR-AUTH-04")));
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
