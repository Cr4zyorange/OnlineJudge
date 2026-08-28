package com.onlinejudge.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = com.onlinejudge.authservice.AuthServiceApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:auth_subject_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "onlinejudge.auth.seed-data-enabled=false"
        }
)
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
class AuthSubjectSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void logoutRevokesOnlyThePresentedSession() throws Exception {
        register("parallel-user", "Parallel01@pass");
        String firstToken = login("parallel-user", "Parallel01@pass");
        String secondToken = login("parallel-user", "Parallel01@pass");

        assertThat(firstToken).isNotEqualTo(secondToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("parallel-user"));
    }

    @Test
    void passwordChangeRevokesEveryExistingSession() throws Exception {
        register("password-user", "Password01@pass");
        String firstToken = login("password-user", "Password01@pass");
        String secondToken = login("password-user", "Password01@pass");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "Password01@pass",
                                "newPassword", "Password02@pass"
                        ))))
                .andExpect(status().isOk());

        for (String token : new String[]{firstToken, secondToken}) {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
        }
    }

    @Test
    void forgedIdentityHeadersNeverCreateATrustedSubject() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
    }

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password,
                                "userType", "STUDENT",
                                "displayName", username,
                                "email", username + "@example.com"
                        ))))
                .andExpect(status().isOk());
    }

    private String login(String account, String password) throws Exception {
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
