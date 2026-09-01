package com.onlinejudge.gradeservice;

import com.onlinejudge.integration.course.CoursePermissionClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grade.rabbit.enabled=false")
@AutoConfigureMockMvc
class GradeApiAuthenticationTest {
    private static KeyPair keyPair;
    private static String jwks;

    @Autowired MockMvc mvc;
    @MockitoBean CoursePermissionClient coursePermissionClient;

    @BeforeAll
    static void createTrustBundle() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        jwks = "{\"keys\":[{\"kid\":\"grade-test\",\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\""
                + unsigned(publicKey.getModulus()) + "\",\"e\":\"" + unsigned(publicKey.getPublicExponent()) + "\"}]}";
    }

    @DynamicPropertySource
    static void identityProperties(DynamicPropertyRegistry properties) {
        properties.add("grade.identity.jwks-trust-bundle", () -> jwks);
        properties.add("grade.identity.refresh-enabled", () -> false);
    }

    @Test
    void rejectsMissingBearerAndResolvesAValidIdentityTokenForExistingGrdRoutes() throws Exception {
        mvc.perform(get("/api/v1/courses/41/grade-items")).andExpect(status().isUnauthorized());

        when(coursePermissionClient.canManageCourseGrade(41, 7)).thenReturn(true);
        mvc.perform(get("/api/v1/courses/41/grade-items")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk());
    }

    private static String token() throws Exception {
        long now = Instant.now().getEpochSecond();
        String header = encode("{\"alg\":\"RS256\",\"kid\":\"grade-test\"}");
        String payload = encode("{\"iss\":\"onlinejudge.identity.v2\",\"aud\":\"onlinejudge.api\",\"iat\":" + now
                + ",\"exp\":" + (now + 300) + ",\"userId\":\"7\",\"username\":\"teacher\","
                + "\"roles\":[\"TEACHER\"],\"permissions\":[\"grade:manage\"],\"securityVersion\":1}");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
