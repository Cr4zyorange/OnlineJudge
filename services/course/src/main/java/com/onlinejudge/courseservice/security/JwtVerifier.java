package com.onlinejudge.courseservice.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local RS256 verifier: it never contacts Identity while serving a request. */
public final class JwtVerifier {
    private static final long CLOCK_SKEW_SECONDS = 30;

    private JwtVerifier() { }

    public static Map<String, PublicKey> parseJwks(ObjectMapper objectMapper, String rawJwks) {
        try {
            Map<String, Object> jwks = objectMapper.readValue(rawJwks, new TypeReference<>() { });
            Object rawKeys = jwks.get("keys");
            if (!(rawKeys instanceof List<?> keys) || keys.isEmpty()) {
                throw new IllegalArgumentException("JWKS keys are required");
            }
            Map<String, PublicKey> result = new LinkedHashMap<>();
            for (Object item : keys) {
                if (!(item instanceof Map<?, ?> key)) {
                    throw new IllegalArgumentException("JWKS key is invalid");
                }
                String kid = requiredString(key.get("kid"));
                if (!"RSA".equals(requiredString(key.get("kty")))
                        || !"sig".equals(requiredString(key.get("use")))
                        || !"RS256".equals(requiredString(key.get("alg")))) {
                    throw new IllegalArgumentException("Only Identity RS256 signing keys are accepted");
                }
                result.put(kid, KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        new BigInteger(1, Base64.getUrlDecoder().decode(requiredString(key.get("n")))),
                        new BigInteger(1, Base64.getUrlDecoder().decode(requiredString(key.get("e"))))
                )));
            }
            return Map.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Identity JWKS trust bundle is invalid", exception);
        }
    }

    public static Claims verify(
            ObjectMapper objectMapper,
            Map<String, PublicKey> keys,
            String rawToken,
            String expectedIssuer,
            String expectedAudience
    ) {
        try {
            String token = stripBearer(rawToken);
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new JwtRejectedException(Reason.MALFORMED);
            }
            Map<String, Object> header = decode(objectMapper, parts[0]);
            if (!"RS256".equals(requiredString(header.get("alg")))) {
                throw new JwtRejectedException(Reason.ALGORITHM);
            }
            PublicKey key = keys.get(requiredString(header.get("kid")));
            if (key == null) {
                throw new JwtRejectedException(Reason.UNKNOWN_KID);
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new JwtRejectedException(Reason.SIGNATURE);
            }
            Map<String, Object> claims = decode(objectMapper, parts[1]);
            if (!expectedIssuer.equals(requiredString(claims.get("iss")))) {
                throw new JwtRejectedException(Reason.ISSUER);
            }
            if (!expectedAudience.equals(requiredString(claims.get("aud")))) {
                throw new JwtRejectedException(Reason.AUDIENCE);
            }
            long now = Clock.systemUTC().instant().getEpochSecond();
            long iat = requiredLong(claims.get("iat"));
            long exp = requiredLong(claims.get("exp"));
            if (iat > now + CLOCK_SKEW_SECONDS || exp <= now - CLOCK_SKEW_SECONDS) {
                throw new JwtRejectedException(Reason.EXPIRED);
            }
            return new Claims(claims);
        } catch (JwtRejectedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JwtRejectedException(Reason.MALFORMED);
        }
    }

    private static Map<String, Object> decode(ObjectMapper objectMapper, String value) throws Exception {
        return objectMapper.readValue(Base64.getUrlDecoder().decode(value), new TypeReference<>() { });
    }

    private static String stripBearer(String value) {
        if (value == null || value.isBlank()) {
            throw new JwtRejectedException(Reason.MALFORMED);
        }
        return value.startsWith("Bearer ") ? value.substring(7).trim() : value.trim();
    }

    public static String requiredString(Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new JwtRejectedException(Reason.MALFORMED);
        }
        return string;
    }

    public static long requiredLong(Object value) {
        if (!(value instanceof Number number) || number.longValue() < 0) {
            throw new JwtRejectedException(Reason.MALFORMED);
        }
        return number.longValue();
    }

    public static List<String> requiredStringList(Object value) {
        if (!(value instanceof List<?> items)) {
            throw new JwtRejectedException(Reason.MALFORMED);
        }
        return items.stream().map(JwtVerifier::requiredString).toList();
    }

    public record Claims(Map<String, Object> values) {
        public String string(String name) { return requiredString(values.get(name)); }
        public long number(String name) { return requiredLong(values.get(name)); }
        public List<String> strings(String name) { return requiredStringList(values.get(name)); }
    }

    public enum Reason { MALFORMED, ALGORITHM, UNKNOWN_KID, SIGNATURE, ISSUER, AUDIENCE, EXPIRED }

    public static final class JwtRejectedException extends RuntimeException {
        private final Reason reason;
        public JwtRejectedException(Reason reason) { this.reason = reason; }
        public Reason reason() { return reason; }
    }
}
