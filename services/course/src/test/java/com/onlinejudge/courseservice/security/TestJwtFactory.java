package com.onlinejudge.courseservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestJwtFactory {
    private static final ObjectMapper JSON = new ObjectMapper();
    private TestJwtFactory() { }

    public static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public static String jwks(String kid, KeyPair pair) {
        try {
            RSAPublicKey key = (RSAPublicKey) pair.getPublic();
            return JSON.writeValueAsString(Map.of("keys", List.of(Map.of(
                    "kty", "RSA", "use", "sig", "alg", "RS256", "kid", kid,
                    "n", url(unsigned(key.getModulus())), "e", url(unsigned(key.getPublicExponent()))))));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public static String userToken(KeyPair pair, String kid, String userId, List<String> roles, List<String> permissions) {
        Map<String, Object> claims = baseClaims();
        claims.put("userId", userId); claims.put("sessionId", "test-session"); claims.put("securityVersion", 1);
        claims.put("roles", roles); claims.put("permissions", permissions); claims.put("aud", "onlinejudge.api");
        return signed(pair, kid, claims);
    }

    public static String serviceToken(KeyPair pair, String kid, String subject, String audience, List<String> scopes) {
        Map<String, Object> claims = baseClaims();
        claims.put("sub", subject); claims.put("scopes", scopes); claims.put("aud", audience);
        return signed(pair, kid, claims);
    }

    private static Map<String, Object> baseClaims() {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "onlinejudge.identity.v2"); claims.put("iat", now); claims.put("exp", now + 300);
        return claims;
    }

    private static String signed(KeyPair pair, String kid, Map<String, Object> claims) {
        try {
            String header = url(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT", "kid", kid)));
            String payload = url(JSON.writeValueAsBytes(claims)); String unsigned = header + "." + payload;
            Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate());
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + url(signature.sign());
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static byte[] unsigned(BigInteger value) { byte[] raw = value.toByteArray(); return raw.length > 1 && raw[0] == 0 ? java.util.Arrays.copyOfRange(raw, 1, raw.length) : raw; }
    private static String url(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
