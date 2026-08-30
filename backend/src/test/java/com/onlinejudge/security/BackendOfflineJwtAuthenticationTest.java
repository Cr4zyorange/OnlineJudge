package com.onlinejudge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.security.SecurityVersionProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The business request boundary must not treat gateway-style identity headers as credentials. */
@SpringBootTest(properties = {
        "onlinejudge.test.legacy-header-auth=false"
})
@ContextConfiguration(initializers = BackendOfflineJwtAuthenticationTest.DeployedTrustBundleInitializer.class)
@AutoConfigureMockMvc
class BackendOfflineJwtAuthenticationTest {
    private static final ObjectMapper TOKENS = new ObjectMapper();
    private static final KeyPair IDENTITY_KEY = keyPair();
    private static final String KID = "identity-test-current";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityVersionProjection securityVersions;

    @Test
    void forgedUserHeadersCannotCreateACourse() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "90901")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"forged-header-course\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deployedTrustBundleAuthorizesAnExistingSessionWithoutCallingIdentity() throws Exception {
        String token = userToken("90902", List.of("TEACHER"), List.of("course:manage"), 1, "onlinejudge.identity.v2", "onlinejudge.api", "RS256", KID, Instant.now().plusSeconds(300));

        mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"offline-jwks-course\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsWrongIssuerAudienceAlgorithmKidExpiryAndStaleSecurityVersion() throws Exception {
        securityVersions.apply("90903", 2);
        for (String token : List.of(
                userToken("90903", List.of("TEACHER"), List.of(), 2, "wrong-issuer", "onlinejudge.api", "RS256", KID, Instant.now().plusSeconds(300)),
                userToken("90903", List.of("TEACHER"), List.of(), 2, "onlinejudge.identity.v2", "wrong-audience", "RS256", KID, Instant.now().plusSeconds(300)),
                userToken("90903", List.of("TEACHER"), List.of(), 2, "onlinejudge.identity.v2", "onlinejudge.api", "HS256", KID, Instant.now().plusSeconds(300)),
                userToken("90903", List.of("TEACHER"), List.of(), 2, "onlinejudge.identity.v2", "onlinejudge.api", "RS256", "unknown-kid", Instant.now().plusSeconds(300)),
                userToken("90903", List.of("TEACHER"), List.of(), 2, "onlinejudge.identity.v2", "onlinejudge.api", "RS256", KID, Instant.now().minusSeconds(60)),
                userToken("90903", List.of("TEACHER"), List.of(), 1, "onlinejudge.identity.v2", "onlinejudge.api", "RS256", KID, Instant.now().plusSeconds(300))
        )) {
            mockMvc.perform(post("/api/v1/courses")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"rejected-token\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static String userToken(
            String userId,
            List<String> roles,
            List<String> permissions,
            long securityVersion,
            String issuer,
            String audience,
            String algorithm,
            String kid,
            Instant expiresAt
    ) throws Exception {
        String header = encode(Map.of("alg", algorithm, "typ", "JWT", "kid", kid));
        String payload = encode(Map.of(
                "userId", userId,
                "roles", roles,
                "permissions", permissions,
                "sessionId", "session-" + userId,
                "securityVersion", securityVersion,
                "iat", Instant.now().getEpochSecond(),
                "exp", expiresAt.getEpochSecond(),
                "iss", issuer,
                "aud", audience
        ));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(IDENTITY_KEY.getPrivate());
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String jwks() {
        RSAPublicKey key = (RSAPublicKey) IDENTITY_KEY.getPublic();
        try {
            return TOKENS.writeValueAsString(Map.of("keys", List.of(Map.of(
                    "kty", "RSA", "use", "sig", "alg", "RS256", "kid", KID,
                    "n", unsigned(key.getModulus()), "e", unsigned(key.getPublicExponent())
            ))));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encode(Object value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(TOKENS.writeValueAsBytes(value));
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }

    /** Mirrors the deployment property source without using DynamicPropertySource. */
    static final class DeployedTrustBundleInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of("onlinejudge.identity.jwks.trust-bundle=" + jwks())
                    .applyTo(context.getEnvironment());
        }
    }
}
