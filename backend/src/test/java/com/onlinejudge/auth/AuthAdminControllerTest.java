package com.onlinejudge.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.controller.RegisterRequest;
import com.onlinejudge.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_admin_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
class AuthAdminControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Test
    void adminAssignsUserRolesAndRolePermissionsWithAuditLogs() throws Exception {
        seedUser("admin46", "Admin46@pass", "ADMIN");
        long targetUserId = seedUser("target46", "Target46@pass", "STUDENT");
        String adminToken = loginToken("admin46", "Admin46@pass");
        long studentRoleId = roleId("STUDENT");
        long teacherRoleId = roleId("TEACHER");
        long authManagePermissionId = permissionId("auth:manage");

        mockMvc.perform(get("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].roleCode", hasItem("STUDENT")))
                .andExpect(jsonPath("$.data[*].roleCode", hasItem("TEACHER")))
                .andExpect(jsonPath("$.data[*].roleCode", hasItem("ADMIN")));

        mockMvc.perform(put("/api/v1/admin/users/{userId}/roles", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleIds", List.of(studentRoleId, teacherRoleId)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(targetUserId))
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")))
                .andExpect(jsonPath("$.data.roles", hasItem("TEACHER")));

        mockMvc.perform(put("/api/v1/admin/roles/{roleId}/permissions", teacherRoleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionIds", List.of(authManagePermissionId)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("TEACHER"))
                .andExpect(jsonPath("$.data.permissions[*].permissionCode", hasItem("auth:manage")));

        assertThat(auditCount("USER_ROLE_UPDATED")).isEqualTo(1);
        assertThat(auditCount("ROLE_PERMISSION_UPDATED")).isEqualTo(1);
    }

    @Test
    void studentCannotAccessRoleManagementApi() throws Exception {
        seedUser("student46", "Student46@pass", "STUDENT");
        String studentToken = loginToken("student46", "Student46@pass");

        mockMvc.perform(get("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-03"));
    }

    @Test
    void studentCannotChangeRolePermissions() throws Exception {
        seedUser("student-permission46", "Student46@pass", "STUDENT");
        String studentToken = loginToken("student-permission46", "Student46@pass");
        long teacherRoleId = roleId("TEACHER");
        long authManagePermissionId = permissionId("auth:manage");

        mockMvc.perform(put("/api/v1/admin/roles/{roleId}/permissions", teacherRoleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionIds", List.of(authManagePermissionId)
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-03"));
    }

    @Test
    void adminCreateUserRollsBackWhenRoleIdDoesNotExist() throws Exception {
        seedUser("admin-rollback46", "Admin46@pass", "ADMIN");
        String adminToken = loginToken("admin-rollback46", "Admin46@pass");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "bad-role46",
                                "password", "BadRole46@pass",
                                "userType", "TEACHER",
                                "displayName", "Bad Role",
                                "email", "bad-role46@example.com",
                                "roleIds", List.of(999999L)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_409"));

        assertThat(userCount("bad-role46")).isZero();
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
        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private long roleId(String roleCode) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT role_id FROM t_auth_role WHERE role_code = ?",
                Long.class,
                roleCode
        );
        return id == null ? -1 : id;
    }

    private long permissionId(String permissionCode) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT permission_id FROM t_auth_permission WHERE permission_code = ?",
                Long.class,
                permissionCode
        );
        return id == null ? -1 : id;
    }

    private int auditCount(String operationType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_audit_log WHERE operation_type = ? AND result_status = 'SUCCESS'",
                Integer.class,
                operationType
        );
        return count == null ? 0 : count;
    }

    private int userCount(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_user WHERE username = ?",
                Integer.class,
                username
        );
        return count == null ? 0 : count;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
