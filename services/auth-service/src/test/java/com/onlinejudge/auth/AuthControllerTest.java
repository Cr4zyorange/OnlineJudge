package com.onlinejudge.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.authservice.AuthServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AuthServiceApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void localFrontendPreviewPortsCanPreflightLoginRequests() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5174")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                        .isEqualTo("http://127.0.0.1:5174"));
    }

    @Test
    void userRegistersLogsInReadsCurrentUserAndLogoutRevokesToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "student45",
                                "password", "Student45@pass",
                                "userType", "STUDENT",
                                "displayName", "学生45",
                                "email", "student45@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.username").value("student45"))
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "student45",
                                "password", "Student45@pass"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("student45"))
                .andExpect(jsonPath("$.data.user.roles", hasItem("STUDENT")))
                .andExpect(jsonPath("$.data.user.permissions").isArray())
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(loginBody);
        String token = body.path("data").path("token").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("student45"))
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")))
                .andExpect(jsonPath("$.data.permissions", not(hasItem(containsString("password")))));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        assertThat(auditCount("LOGOUT", "SUCCESS")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_id FROM t_auth_audit_log WHERE operation_type = 'LOGOUT'",
                String.class
        )).matches("[0-9a-f]{64}");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
    }

    @Test
    void loginFailureUsesSafeMessageAndDoesNotCreateSession() throws Exception {
        registerStudent("student46", "Student46@pass", "student46@example.com", "13900000046");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "student46",
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-01"))
                .andExpect(jsonPath("$.message").value("账号或密码错误"))
                .andExpect(jsonPath("$.message").value(not(containsString("不存在"))));
        assertThat(auditCount("LOGIN_FAILURE", "FAILURE")).isEqualTo(1);
    }

    @Test
    void logoutRequiresAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
    }

    @Test
    void logoutRejectsForgedBearerTokenWithoutLeakingToken() throws Exception {
        String forgedToken = "forged.token.value";

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"))
                .andExpect(jsonPath("$.message").value("登录已失效，请重新登录"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(forgedToken));
    }

    @Test
    void currentUserRequiresBearerSessionInsteadOfHeaderOnlyIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-User-Id", "45")
                        .header("X-User-Name", "student45")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
    }

    @Test
    void publicRegistrationRejectsTeacherAndAdminRoles() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin45",
                                "password", "Admin45@pass",
                                "userType", "ADMIN",
                                "displayName", "管理员45"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "teacher45",
                                "password", "Teacher45@pass",
                                "userType", "TEACHER",
                                "displayName", "教师45"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400"));
    }

    @Test
    void registrationRejectsDuplicateEmailAndPhoneUsedForLogin() throws Exception {
        registerStudent("student47", "Student47@pass", "student47@example.com", "13900000047");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "student48",
                                "password", "Student48@pass",
                                "userType", "STUDENT",
                                "displayName", "学生48",
                                "email", "student47@example.com"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_409"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "student49",
                                "password", "Student49@pass",
                                "userType", "STUDENT",
                                "displayName", "学生49",
                                "phone", "13900000047"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_409"));
    }

    @Test
    void sessionStoresTokenDigestInsteadOfBearerTokenPlaintextAndAuditsLogin() throws Exception {
        registerStudent("student50", "Student50@pass", "student50@example.com", "13900000050");

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "student50@example.com",
                                "password", "Student50@pass"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String bearerToken = objectMapper.readTree(loginBody).path("data").path("token").asText();
        String storedTokenId = jdbcTemplate.queryForObject("SELECT token_id FROM t_auth_session", String.class);

        assertThat(storedTokenId).isNotEqualTo(bearerToken);
        assertThat(storedTokenId).matches("[0-9a-f]{64}");
        assertThat(auditCount("LOGIN_SUCCESS", "SUCCESS")).isEqualTo(1);
    }

    @Test
    void protectedAuthApiRejectsHeaderOnlyIdentityWhenSessionTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-User-Id", "501")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
    }

    @Test
    void expiredOrRevokedSessionUsesDocumentedSessionExpiredErrorCode() throws Exception {
        registerStudent("student-session49", "Student49@pass", "session49@example.com", "13900000049");
        String token = loginToken("student-session49", "Student49@pass");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"))
                .andExpect(jsonPath("$.message").value("登录已失效，请重新登录"));
    }

    @Test
    void checkPermissionAllowsCurrentUserPermission() throws Exception {
        registerStudent("student51", "Student51@pass", "student51@example.com", "13900000051");
        String token = loginToken("student51", "Student51@pass");

        mockMvc.perform(post("/api/v1/auth/check-permission")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionCode", "course:view",
                                "resourceType", "COURSE",
                                "resourceId", "101"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.permissionCode").value("course:view"));
    }

    @Test
    void checkPermissionRejectsMissingPermissionAndAuditsDeniedAccess() throws Exception {
        registerStudent("student52", "Student52@pass", "student52@example.com", "13900000052");
        String token = loginToken("student52", "Student52@pass");

        mockMvc.perform(post("/api/v1/auth/check-permission")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionCode", "auth:manage",
                                "resourceType", "AUTH_ADMIN",
                                "resourceId", "roles"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"))
                .andExpect(jsonPath("$.message").value("无权限访问"));

        assertThat(auditCount("ACCESS_DENIED", "DENIED")).isEqualTo(1);
    }

    @Test
    void checkPermissionRejectsBlankPermissionCode() throws Exception {
        registerStudent("student53", "Student53@pass", "student53@example.com", "13900000053");
        String token = loginToken("student53", "Student53@pass");

        mockMvc.perform(post("/api/v1/auth/check-permission")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionCode", " "
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400"))
                .andExpect(jsonPath("$.message").value("权限编码不能为空"));
    }

    @Test
    void authInterceptorProtectsAdminEndpointWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
    }

    @Test
    void malformedAuthJsonReturnsSafeValidationErrorWithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"student51","password":
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"))
                .andExpect(jsonPath("$.message").value(not(containsString("JsonParseException"))))
                .andExpect(jsonPath("$.message").value(not(containsString("HttpMessageNotReadableException"))))
                .andExpect(jsonPath("$.message").value(not(containsString("password"))));
    }

    @Test
    void forgedBearerTokenUsesSafeAuthenticationFailureMessage() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"))
                .andExpect(jsonPath("$.message").value("登录已失效，请重新登录"))
                .andExpect(jsonPath("$.message").value(not(containsString("forged.token.value"))));
    }

    @Test
    void currentUserProfileCanBeReadAndUpdatedWithoutSensitiveFields() throws Exception {
        registerStudent("student54", "Student54@pass", "student54@example.com", "13900000054");
        String token = loginToken("student54", "Student54@pass");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.username").value("student54"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.passwordSalt").doesNotExist());

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "瀛︾敓54-更新",
                                "phone", "13900000954",
                                "email", "student54-new@example.com",
                                "avatarUrl", "https://example.com/avatar54.png"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.username").value("student54"))
                .andExpect(jsonPath("$.data.displayName").value("瀛︾敓54-更新"))
                .andExpect(jsonPath("$.data.phone").value("13900000954"))
                .andExpect(jsonPath("$.data.email").value("student54-new@example.com"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar54.png"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void profileUpdateRejectsInvalidContactAndDisplayNameBeforeSaving() throws Exception {
        registerStudent("student57", "Student57@pass", "student57@example.com", "13900000057");
        String token = loginToken("student57", "Student57@pass");

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "student57",
                                "phone", "not-a-phone",
                                "email", "student57-new@example.com"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400"))
                .andExpect(jsonPath("$.message").value("手机号格式不正确"));

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "student57",
                                "phone", "13900000957",
                                "email", "not-an-email"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400"))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "a".repeat(65),
                                "phone", "13900000957",
                                "email", "student57-new@example.com"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400"))
                .andExpect(jsonPath("$.message").value("显示名称长度不能超过64个字符"));

        mockMvc.perform(put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "student57",
                                "phone", "13900000957",
                                "email", "student57-new@example.com",
                                "avatarUrl", "https://example.com/" + "a".repeat(256)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400"))
                .andExpect(jsonPath("$.message").value("头像地址长度不能超过255个字符"));

        String storedEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM t_auth_user WHERE username = 'student57'",
                String.class
        );
        assertThat(storedEmail).isEqualTo("student57@example.com");
    }

    @Test
    void passwordChangeRequiresOldPasswordRehashesAndRevokesExistingSessions() throws Exception {
        registerStudent("student55", "Student55@pass", "student55@example.com", "13900000055");
        String token = loginToken("student55", "Student55@pass");
        String oldHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM t_auth_user WHERE username = 'student55'",
                String.class
        );

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "wrong-password",
                                "newPassword", "Student55@new"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"))
                .andExpect(jsonPath("$.message").value("原密码错误"));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "Student55@pass",
                                "newPassword", "Student55@new"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        String newHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM t_auth_user WHERE username = 'student55'",
                String.class
        );
        assertThat(newHash).isNotEqualTo(oldHash);
        assertThat(newHash).doesNotContain("Student55@new");
        assertThat(auditCount("PASSWORD_CHANGED", "SUCCESS")).isEqualTo(1);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "student55",
                                "password", "Student55@pass"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-01"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "student55",
                                "password", "Student55@new"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void repeatedLoginFailuresSetTemporaryLockUntilTimestamp() throws Exception {
        registerStudent("student56", "Student56@pass", "student56@example.com", "13900000056");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "account", "student56",
                                    "password", "bad-password"
                            ))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ERR-AUTH-01"));
        }

        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT failed_login_count FROM t_auth_user WHERE username = 'student56'",
                Integer.class
        );
        String accountStatus = jdbcTemplate.queryForObject(
                "SELECT account_status FROM t_auth_user WHERE username = 'student56'",
                String.class
        );
        assertThat(failedCount).isEqualTo(5);
        assertThat(accountStatus).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT locked_until IS NOT NULL FROM t_auth_user WHERE username = 'student56'",
                Boolean.class
        )).isTrue();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "student56",
                                "password", "Student56@pass"
                ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-03"));
    }

    private void registerStudent(String username, String password, String email, String phone) throws Exception {
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
    }

    private int auditCount(String operationType, String resultStatus) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_auth_audit_log WHERE operation_type = ? AND result_status = ?",
                Integer.class,
                operationType,
                resultStatus
        );
        return count == null ? 0 : count;
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
}
