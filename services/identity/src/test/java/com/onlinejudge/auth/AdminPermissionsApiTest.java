package com.onlinejudge.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.controller.RegisterRequest;
import com.onlinejudge.auth.service.AuthService;
import com.onlinejudge.identityservice.IdentityServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #367 API coverage: the three admin collection endpoints that the
 * existing suite did not exercise through HTTP (permissions list, user list
 * and role creation) get dedicated contract tests in the Identity service.
 */
@SpringBootTest(classes = IdentityServiceApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_permissions_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
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
class AdminPermissionsApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Test
    void adminCanListAllPermissionCodesThroughHttpApi() throws Exception {
        seedAdmin();
        String adminToken = loginToken("admin-api", "AdminApi1@pass");

        mockMvc.perform(get("/api/v1/admin/permissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[*].permissionCode", hasItem("course:view")));
    }

    @Test
    void adminCanListUsersThroughHttpApiAndResponseHidesPasswordFields() throws Exception {
        seedAdmin();
        String adminToken = loginToken("admin-api", "AdminApi1@pass");
        authService.registerTrusted(new RegisterRequest(
                "list-target-api", "TargetApi1@pass", "STUDENT",
                "目标学生", null, "list-target-api@example.com", null
        ), "STUDENT");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.records[*].username", hasItem("list-target-api")))
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist());
    }

    @Test
    void adminCanCreateRoleThroughHttpApiAndRoleListIncludesIt() throws Exception {
        seedAdmin();
        String adminToken = loginToken("admin-api", "AdminApi1@pass");

        mockMvc.perform(post("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleCode", "COURSE_DESIGNER",
                                "roleName", "课程设计师",
                                "description", "API 契约测试创建",
                                "enabled", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.roleCode").value("COURSE_DESIGNER"))
                .andExpect(jsonPath("$.data.roleName").value("课程设计师"));

        mockMvc.perform(get("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].roleCode", hasItem("COURSE_DESIGNER")));
    }

    @Test
    void permissionListEndpointRejectsMissingBearerWithSessionExpiredError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
    }

    private long seedAdmin() {
        return authService.registerTrusted(new RegisterRequest(
                "admin-api", "AdminApi1@pass", "ADMIN",
                "管理员", null, "admin-api@example.com", null
        ), "ADMIN").id();
    }

    private String loginToken(String username, String password) throws Exception {
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
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
