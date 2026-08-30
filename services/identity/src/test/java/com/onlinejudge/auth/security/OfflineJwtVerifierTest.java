package com.onlinejudge.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineJwtVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void businessServiceVerifiesCachedJwksWhileIdentityIsUnavailableAndRejectsInvalidClaims() throws Exception {
        KeyPair pair = keyPair();
        OfflineJwtVerifier verifier = OfflineJwtVerifier.fromJwks(
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "onlinejudge.identity.v2",
                "onlinejudge.api",
                jwks("kid-old", pair)
        );
        String valid = signedToken(pair, "kid-old", "onlinejudge.api", NOW.plusSeconds(300), 4, "RS256");

        assertThat(verifier.verify(valid, userId -> 4).accepted()).isTrue();
        assertThat(verifier.verify(
                signedToken(pair, "kid-old", "wrong-audience", NOW.plusSeconds(300), 4, "RS256"),
                userId -> 4
        ).rejection()).isEqualTo(OfflineJwtVerifier.Rejection.AUDIENCE_MISMATCH);
        assertThat(OfflineJwtVerifier.fromJwks(
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "other-issuer",
                "onlinejudge.api",
                jwks("kid-old", pair)
        ).verify(valid, userId -> 4).rejection()).isEqualTo(OfflineJwtVerifier.Rejection.ISSUER_MISMATCH);
        assertThat(verifier.verify(
                signedToken(pair, "kid-old", "onlinejudge.api", NOW.minusSeconds(31), 4, "RS256"),
                userId -> 4
        ).rejection()).isEqualTo(OfflineJwtVerifier.Rejection.EXPIRED);
        assertThat(verifier.verify(valid, userId -> 5).rejection())
                .isEqualTo(OfflineJwtVerifier.Rejection.SECURITY_VERSION_STALE);
        assertThat(verifier.verify(
                signedToken(pair, "kid-old", "onlinejudge.api", NOW.plusSeconds(300), 4, "HS256"),
                userId -> 4
        ).rejection()).isEqualTo(OfflineJwtVerifier.Rejection.ALGORITHM_NOT_ALLOWED);
    }

    @Test
    void keyRotationKeepsThePriorKidValidThroughoutTheOverlapWindow() throws Exception {
        KeyPair oldPair = keyPair();
        KeyPair newPair = keyPair();
        OfflineJwtVerifier verifier = OfflineJwtVerifier.fromJwks(
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "onlinejudge.identity.v2",
                "onlinejudge.api",
                jwks("kid-old", oldPair)
        );

        assertThat(verifier.verify(
                signedToken(oldPair, "kid-old", "onlinejudge.api", NOW.plusSeconds(300), 1, "RS256"),
                userId -> 1
        ).accepted()).isTrue();
        String rotatedToken = signedToken(newPair, "kid-new", "onlinejudge.api", NOW.plusSeconds(300), 1, "RS256");
        assertThat(verifier.verify(rotatedToken, userId -> 1).rejection())
                .isEqualTo(OfflineJwtVerifier.Rejection.UNKNOWN_KID);
        verifier = verifier.withRefreshedJwks(jwks("kid-old", oldPair, "kid-new", newPair));
        assertThat(verifier.verify(
                rotatedToken,
                userId -> 1
        ).accepted()).isTrue();
    }

    private String signedToken(
            KeyPair pair,
            String kid,
            String audience,
            Instant expiry,
            long securityVersion,
            String algorithm
    ) throws Exception {
        String header = encode(Map.of("alg", algorithm, "typ", "JWT", "kid", kid));
        String payload = encode(Map.of(
                "userId", "42",
                "roles", List.of("STUDENT"),
                "permissions", List.of("course:view"),
                "sessionId", "session-42",
                "securityVersion", securityVersion,
                "iat", NOW.getEpochSecond(),
                "exp", expiry.getEpochSecond(),
                "iss", "onlinejudge.identity.v2",
                "aud", audience
        ));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private Map<String, Object> jwks(Object... values) {
        java.util.ArrayList<Map<String, String>> keys = new java.util.ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            String kid = (String) values[index];
            RSAPublicKey key = (RSAPublicKey) ((KeyPair) values[index + 1]).getPublic();
            keys.add(Map.of(
                    "kty", "RSA",
                    "use", "sig",
                    "alg", "RS256",
                    "kid", kid,
                    "n", unsigned(key.getModulus()),
                    "e", unsigned(key.getPublicExponent())
            ));
        }
        return Map.of("keys", keys);
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String encode(Object value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }
}
