package com.onlinejudge.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = com.onlinejudge.identityservice.IdentityServiceApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:identity_jwt_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
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
class IdentityJwtContractTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginIssuesRs256UserJwtWithTheFrozenV2Claims() throws Exception {
        register("identity-jwt-user", "Identity01@pass");

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "account", "identity-jwt-user",
                                "password", "Identity01@pass"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(response).path("data").path("token").asText();
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        assertThat(header.path("kid").asText()).isNotBlank();
        assertThat(claims.path("iss").asText()).isEqualTo("onlinejudge.identity.v2");
        assertThat(claims.path("aud").asText()).isEqualTo("onlinejudge.api");
        assertThat(claims.path("userId").asText()).isNotBlank();
        assertThat(claims.path("sessionId").asText()).isNotBlank();
        assertThat(claims.path("securityVersion").asLong()).isPositive();
        assertThat(claims.path("roles").isArray()).isTrue();
        assertThat(claims.path("permissions").isArray()).isTrue();
        assertThat(claims.path("iat").asLong()).isPositive();
        assertThat(claims.path("exp").asLong()).isGreaterThan(claims.path("iat").asLong());
    }

    @Test
    void publishesPublicSigningKeysAtTheStandardJwksEndpoint() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_ID_REQUIRED"))
                .andExpect(jsonPath("$.requestId").value("unknown"));

        String response = mockMvc.perform(get("/.well-known/jwks.json")
                        .header("X-Request-Id", "jwks-contract-request"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode key = objectMapper.readTree(response).path("keys").get(0);
        assertThat(key.path("kty").asText()).isEqualTo("RSA");
        assertThat(key.path("use").asText()).isEqualTo("sig");
        assertThat(key.path("alg").asText()).isEqualTo("RS256");
        assertThat(key.path("kid").asText()).isNotBlank();
        assertThat(key.path("n").asText()).isNotBlank();
        assertThat(key.path("e").asText()).isNotBlank();
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
}
