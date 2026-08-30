package com.onlinejudge.assessmentservice.security;

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
    public static KeyPair rsaKeyPair() { try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); } catch (Exception e) { throw new IllegalStateException(e); } }
    public static String jwks(String kid, KeyPair pair) { try { RSAPublicKey key = (RSAPublicKey) pair.getPublic(); return JSON.writeValueAsString(Map.of("keys", List.of(Map.of("kty", "RSA", "use", "sig", "alg", "RS256", "kid", kid, "n", url(unsigned(key.getModulus())), "e", url(unsigned(key.getPublicExponent())))))); } catch (Exception e) { throw new IllegalStateException(e); } }
    public static String userToken(KeyPair pair, String kid, String userId, List<String> roles) { var claims = new LinkedHashMap<String, Object>(); long now = Instant.now().getEpochSecond(); claims.put("iss", "onlinejudge.identity.v2"); claims.put("aud", "onlinejudge.api"); claims.put("iat", now); claims.put("exp", now + 300); claims.put("userId", userId); claims.put("sessionId", "session-1"); claims.put("securityVersion", 1); claims.put("roles", roles); claims.put("permissions", List.of()); return signed(pair, kid, claims); }
    private static String signed(KeyPair pair, String kid, Map<String, Object> claims) { try { String header = url(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT", "kid", kid))); String payload = url(JSON.writeValueAsBytes(claims)); String unsigned = header + "." + payload; Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate()); signature.update(unsigned.getBytes(StandardCharsets.US_ASCII)); return unsigned + "." + url(signature.sign()); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static byte[] unsigned(BigInteger value) { byte[] raw = value.toByteArray(); return raw.length > 1 && raw[0] == 0 ? java.util.Arrays.copyOfRange(raw, 1, raw.length) : raw; }
    private static String url(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
