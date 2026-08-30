package com.onlinejudge.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.controller.RegisterRequest;
import com.onlinejudge.auth.security.JwtTokenService;
import com.onlinejudge.auth.security.OfflineJwtVerifier;
import com.onlinejudge.auth.service.AuthService;
import com.onlinejudge.identityservice.IdentityServiceApplication;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IdentityServiceApplication.class, properties = {
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

    @Autowired
    private JwtTokenService jwtTokenService;

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
    void adminQueriesAuditLogsWithOperationResultOperatorAndTimeFilters() throws Exception {
        long adminId = seedUser("admin-audit50", "Admin50@pass", "ADMIN");
        seedUser("student-audit50", "Student50@pass", "STUDENT");
        String adminToken = loginToken("admin-audit50", "Admin50@pass");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.50")
                        .header(HttpHeaders.USER_AGENT, "AuditTest/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "student-audit50",
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("operatorId", String.valueOf(adminId))
                        .param("operationType", "LOGIN_SUCCESS")
                        .param("resultStatus", "SUCCESS")
                        .param("startTime", "2026-01-01T00:00:00")
                        .param("endTime", "2099-12-31T23:59:59")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].operatorId").value(adminId))
                .andExpect(jsonPath("$.data.records[0].operationType").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].targetType").value("AUTH_USER"))
                .andExpect(jsonPath("$.data.records[0].resultStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].createdAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("operationType", "LOGIN_FAILURE")
                        .param("resultStatus", "FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].clientIp").value("203.0.113.50"))
                .andExpect(jsonPath("$.data.records[0].userAgent").value("AuditTest/50"))
                .andExpect(jsonPath("$.data.records[0].failureReason").isNotEmpty());
    }

    @Test
    void loginAuditBoundsClientIpAndUserAgentToColumnLimits() throws Exception {
        seedUser("admin-header50", "Admin50@pass", "ADMIN");
        String longForwardedFor = "203.0.113." + "5".repeat(80);
        String longUserAgent = "AuditTest/" + "5".repeat(300);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", longForwardedFor + ", 198.51.100.1")
                        .header(HttpHeaders.USER_AGENT, longUserAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "admin-header50",
                                "password", "Admin50@pass"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        Map<String, Object> auditClient = jdbcTemplate.queryForMap("""
                SELECT client_ip, user_agent
                FROM t_auth_audit_log
                WHERE operation_type = 'LOGIN_SUCCESS'
                """);
        assertThat((String) auditClient.get("CLIENT_IP")).hasSize(64);
        assertThat((String) auditClient.get("USER_AGENT")).hasSize(255);
    }

    @Test
    void studentCannotAccessRoleManagementApi() throws Exception {
        seedUser("student46", "Student46@pass", "STUDENT");
        String studentToken = loginToken("student46", "Student46@pass");

        mockMvc.perform(get("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_audit_log WHERE operation_type = ? AND result_status = 'FAILURE'",
                Integer.class,
                "ADMIN_ACCESS_DENIED"
        )).isEqualTo(1);
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
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));
    }

    @Test
    void adminDisablingAccountRevokesExistingSessionsAndReportsAccountStatusError() throws Exception {
        seedUser("admin-status49", "Admin49@pass", "ADMIN");
        long targetUserId = seedUser("target-status49", "Target49@pass", "STUDENT");
        String adminToken = loginToken("admin-status49", "Admin49@pass");
        String targetToken = loginToken("target-status49", "Target49@pass");

        mockMvc.perform(put("/api/v1/admin/users/{userId}/status", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountStatus", "DISABLED"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("DISABLED"));

        String sessionStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM t_auth_session WHERE user_id = ?",
                String.class,
                targetUserId
        );
        assertThat(sessionStatus).isEqualTo("REVOKED");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(targetToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-03"))
                .andExpect(jsonPath("$.message").value("账号状态异常，请联系管理员"));
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

    @Test
    void adminCreateUserDoesNotExposePasswordInResponseOrAuditLogs() throws Exception {
        seedUser("admin-safe51", "Admin51@pass", "ADMIN");
        String adminToken = loginToken("admin-safe51", "Admin51@pass");

        String response = mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "safe-user51",
                                "password", "SafeUser51@pass",
                                "userType", "TEACHER",
                                "displayName", "Safe User",
                                "email", "safe-user51@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("safe-user51"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.passwordSalt").doesNotExist())
                .andExpect(jsonPath("$.message").value(not(containsString("SafeUser51@pass"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("SafeUser51@pass");

        assertThat(auditCount("USER_CREATED")).isEqualTo(1);
        String auditText = jdbcTemplate.queryForObject("""
                SELECT COALESCE(target_id, '') || ' ' || COALESCE(failure_reason, '')
                FROM t_auth_audit_log
                WHERE operation_type = 'USER_CREATED'
                """, String.class);
        assertThat(auditText).doesNotContain("SafeUser51@pass");
        assertThat(auditText).doesNotContain("password");
    }

    @Test
    void adminUpdatesRoleThroughDocumentedCollectionEndpoint() throws Exception {
        seedUser("admin-role-path46", "Admin46@pass", "ADMIN");
        String adminToken = loginToken("admin-role-path46", "Admin46@pass");
        long teacherRoleId = roleId("TEACHER");

        mockMvc.perform(put("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleId", teacherRoleId,
                                "roleCode", "TEACHER",
                                "roleName", "授课教师",
                                "description", "负责课程教学",
                                "enabled", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(teacherRoleId))
                .andExpect(jsonPath("$.data.roleName").value("授课教师"));
    }

    @Test
    void disablingRoleAdvancesEveryAffectedSecurityVersionAndInvalidatesOldJwtOffline() throws Exception {
        long adminId = seedUser("disable-admin", "DisableAdmin01@pass", "ADMIN");
        long targetId = seedUser("disable-target", "DisableTarget01@pass", "ADMIN");
        String adminToken = loginToken("disable-admin", "DisableAdmin01@pass");
        String targetToken = loginToken("disable-target", "DisableTarget01@pass");
        long adminRoleId = roleId("ADMIN");

        mockMvc.perform(put("/api/v1/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleId", adminRoleId,
                                "roleCode", "ADMIN",
                                "roleName", "管理员",
                                "enabled", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(securityVersion(adminId)).isEqualTo(2L);
        assertThat(securityVersion(targetId)).isEqualTo(2L);
        OfflineJwtVerifier verifier = OfflineJwtVerifier.fromJwks(
                objectMapper,
                java.time.Clock.systemUTC(),
                JwtTokenService.ISSUER,
                JwtTokenService.USER_AUDIENCE,
                jwtTokenService.jwks()
        );
        assertThat(verifier.verify(targetToken, ignored -> securityVersion(targetId)).accepted()).isFalse();
        assertThat(verifier.verify(targetToken, ignored -> securityVersion(targetId)).rejection())
                .isEqualTo(OfflineJwtVerifier.Rejection.SECURITY_VERSION_STALE);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(targetToken)))
                .andExpect(status().isUnauthorized());
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

    private long securityVersion(long userId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT security_version FROM t_auth_user WHERE user_id = ?",
                Long.class,
                userId
        );
        return value == null ? -1 : value;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
