package com.onlinejudge.gradeservice.security;

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

/** Local RS256 validation; request handling never calls Identity synchronously. */
final class GradeJwtVerifier {
    private static final long CLOCK_SKEW_SECONDS = 30;
    private GradeJwtVerifier() { }

    static Map<String, PublicKey> parseJwks(ObjectMapper json, String raw) {
        try {
            Map<String, Object> jwks = json.readValue(raw, new TypeReference<>() { });
            if (!(jwks.get("keys") instanceof List<?> keys) || keys.isEmpty()) throw new IllegalArgumentException();
            Map<String, PublicKey> result = new LinkedHashMap<>();
            for (Object item : keys) {
                if (!(item instanceof Map<?, ?> key) || !"RSA".equals(requiredString(key.get("kty")))
                        || !"sig".equals(requiredString(key.get("use"))) || !"RS256".equals(requiredString(key.get("alg")))) {
                    throw new IllegalArgumentException();
                }
                result.put(requiredString(key.get("kid")), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        new BigInteger(1, Base64.getUrlDecoder().decode(requiredString(key.get("n")))),
                        new BigInteger(1, Base64.getUrlDecoder().decode(requiredString(key.get("e")))))));
            }
            return Map.copyOf(result);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Identity JWKS trust bundle is invalid", invalid);
        }
    }

    static Claims verify(ObjectMapper json, Map<String, PublicKey> keys, String authorization,
                         String issuer, String audience) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new Rejected();
            String[] parts = authorization.substring(7).trim().split("\\.", -1);
            if (parts.length != 3) throw new Rejected();
            Map<String, Object> header = decode(json, parts[0]);
            if (!"RS256".equals(requiredString(header.get("alg")))) throw new Rejected();
            PublicKey key = keys.get(requiredString(header.get("kid")));
            if (key == null) throw new UnknownKid();
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) throw new Rejected();
            Map<String, Object> claims = decode(json, parts[1]);
            if (!issuer.equals(requiredString(claims.get("iss"))) || !audience.equals(requiredString(claims.get("aud")))) throw new Rejected();
            long now = Instant.now().getEpochSecond();
            long iat = requiredLong(claims.get("iat"));
            long exp = requiredLong(claims.get("exp"));
            if (iat > now + CLOCK_SKEW_SECONDS || exp <= now - CLOCK_SKEW_SECONDS) throw new Rejected();
            return new Claims(claims);
        } catch (UnknownKid unknownKid) {
            throw unknownKid;
        } catch (Rejected rejected) {
            throw rejected;
        } catch (Exception invalid) {
            throw new Rejected();
        }
    }

    private static Map<String, Object> decode(ObjectMapper json, String value) throws Exception {
        return json.readValue(Base64.getUrlDecoder().decode(value), new TypeReference<>() { });
    }
    private static String requiredString(Object value) {
        if (!(value instanceof String string) || string.isBlank()) throw new Rejected();
        return string;
    }
    private static long requiredLong(Object value) {
        if (!(value instanceof Number number) || number.longValue() < 0) throw new Rejected();
        return number.longValue();
    }
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) throw new Rejected();
        return list.stream().map(GradeJwtVerifier::requiredString).toList();
    }

    record Claims(Map<String, Object> values) {
        String string(String name) { return requiredString(values.get(name)); }
        List<String> strings(String name) { return stringList(values.get(name)); }
    }
    static class Rejected extends RuntimeException { }
    static final class UnknownKid extends Rejected { }
}
