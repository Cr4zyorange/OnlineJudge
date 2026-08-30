package com.onlinejudge.assessmentservice.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure local RS256 verifier; request handling never synchronously calls Identity. */
final class JwtVerifier {
    private JwtVerifier() { }
    static Map<String, PublicKey> parseJwks(ObjectMapper mapper, String raw) {
        try {
            Object keysValue = mapper.readValue(raw, new TypeReference<Map<String, Object>>() {}).get("keys");
            if (!(keysValue instanceof List<?> keys) || keys.isEmpty()) throw new IllegalArgumentException("keys missing");
            Map<String, PublicKey> result = new LinkedHashMap<>();
            for (Object item : keys) {
                if (!(item instanceof Map<?, ?> key) || !"RSA".equals(string(key.get("kty"))) || !"sig".equals(string(key.get("use"))) || !"RS256".equals(string(key.get("alg")))) throw new IllegalArgumentException("only RS256 signing JWKS keys are accepted");
                result.put(string(key.get("kid")), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1, Base64.getUrlDecoder().decode(string(key.get("n")))), new BigInteger(1, Base64.getUrlDecoder().decode(string(key.get("e")))))));
            }
            return Map.copyOf(result);
        } catch (Exception e) { throw new Rejected("malformed JWKS", e); }
    }
    static Claims verify(ObjectMapper mapper, Map<String, PublicKey> keys, String authorization, String issuer, String audience) {
        try {
            String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : "";
            String[] parts = token.split("\\.", -1); if (parts.length != 3) throw new Rejected("malformed token");
            Map<String, Object> header = json(mapper, parts[0]);
            if (!"RS256".equals(string(header.get("alg")))) throw new Rejected("algorithm rejected");
            PublicKey key = keys.get(string(header.get("kid"))); if (key == null) throw new UnknownKid();
            Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key); verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) throw new Rejected("signature rejected");
            Map<String, Object> claims = json(mapper, parts[1]);
            if (!issuer.equals(string(claims.get("iss"))) || !audience.equals(string(claims.get("aud")))) throw new Rejected("issuer or audience rejected");
            long now = Instant.now().getEpochSecond(); long iat = number(claims.get("iat")); long exp = number(claims.get("exp"));
            if (iat > now + 30 || exp <= now - 30) throw new Rejected("expired token");
            return new Claims(claims);
        } catch (Rejected e) { throw e; } catch (Exception e) { throw new Rejected("malformed token", e); }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> json(ObjectMapper mapper, String part) throws Exception { return mapper.readValue(Base64.getUrlDecoder().decode(part), new TypeReference<Map<String, Object>>() {}); }
    static String string(Object value) { if (!(value instanceof String s) || s.isBlank()) throw new Rejected("required claim missing"); return s; }
    static long number(Object value) { if (!(value instanceof Number n) || n.longValue() < 0) throw new Rejected("numeric claim missing"); return n.longValue(); }
    static List<String> strings(Object value) { if (!(value instanceof List<?> list)) throw new Rejected("list claim missing"); return list.stream().map(JwtVerifier::string).toList(); }
    record Claims(Map<String, Object> values) { String string(String n) { return JwtVerifier.string(values.get(n)); } long number(String n) { return JwtVerifier.number(values.get(n)); } List<String> strings(String n) { return JwtVerifier.strings(values.get(n)); } }
    static class Rejected extends RuntimeException { Rejected(String message) { super(message); } Rejected(String message, Throwable cause) { super(message, cause); } }
    static final class UnknownKid extends Rejected { UnknownKid() { super("unknown kid"); } }
}
