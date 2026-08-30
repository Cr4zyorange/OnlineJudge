package com.onlinejudge.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.identityservice.IdentityServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IdentityServiceApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:identity_service_tokens;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.seed-data-enabled=false",
        "onlinejudge.identity.service-tokens.workloads={\"CN=course-service\":{\"audiences\":[\"course\"],\"scopes\":[\"course:read\",\"course:write\"]}}"
})
@AutoConfigureMockMvc
class ServiceTokenControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mtlsWorkloadMintsAShortLivedAudienceBoundServiceToken() throws Exception {
        String response = mockMvc.perform(mint(courseCertificate(), "course", "course:read", "service-token-idempotency-0001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audience").value("course"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode claims = jwtClaims(objectMapper.readTree(response).path("accessToken").asText());
        assertThat(claims.path("iss").asText()).isEqualTo("onlinejudge.identity.v2");
        assertThat(claims.path("aud").asText()).isEqualTo("course");
        assertThat(claims.path("sub").asText()).isEqualTo("CN=course-service");
        assertThat(claims.path("scopes").get(0).asText()).isEqualTo("course:read");
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isBetween(1L, 300L);
    }

    @Test
    void rejectsMissingWorkloadIdentityAndUnauthorizedAudienceWithCanonicalFailures() throws Exception {
        mockMvc.perform(mint(null, "course", "course:read", "service-token-idempotency-0002"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_INVALID"))
                .andExpect(jsonPath("$.requestId").value("request-service-token-test"))
                .andExpect(jsonPath("$.retryable").value(false));

        mockMvc.perform(mint(courseCertificate(), "grade", "course:read", "service-token-idempotency-0003"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_FORBIDDEN"));
    }

    @Test
    void rejectsIdempotencyKeyReuseForADifferentServiceTokenRequest() throws Exception {
        mockMvc.perform(mint(courseCertificate(), "course", "course:read", "service-token-idempotency-0004"))
                .andExpect(status().isCreated());

        mockMvc.perform(mint(courseCertificate(), "course", "course:write", "service-token-idempotency-0004"))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsTheCanonicalErrorShapeForMalformedServiceTokenRequests() throws Exception {
        mockMvc.perform(post("/internal/v2/service-tokens")
                        .header("X-Request-Id", "request-service-token-malformed")
                        .header("Idempotency-Key", "service-token-idempotency-0005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json")
                        .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{courseCertificate()}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERVICE_TOKEN_INVALID"))
                .andExpect(jsonPath("$.requestId").value("request-service-token-malformed"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder mint(
            X509Certificate certificate, String audience, String scope, String idempotencyKey
    ) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = post("/internal/v2/service-tokens")
                .header("X-Request-Id", "request-service-token-test")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("audience", audience, "scopes", java.util.List.of(scope))));
        if (certificate != null) {
            request.requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{certificate});
        }
        return request;
    }

    private X509Certificate courseCertificate() {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=course-service"));
        return certificate;
    }

    private JsonNode jwtClaims(String token) throws Exception {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    }
}
