package com.onlinejudge.lrn.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Frozen v2 service-identity protocol verifier for Course's LRN internal
 * endpoints: an RS256 service JWT signed by Identity, audience-bound to
 * {@code course}, carrying a {@code scopes} list.  Request handling never
 * calls Identity synchronously; only the last valid JWKS snapshot is used.
 */
@Component
public class ServiceJwtVerifier {
    public static final String ALGORITHM = "RS256";
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final ObjectMapper objectMapper;
    private final String issuer;
    private final String audience;
    private final AtomicReference<Map<String, PublicKey>> keys = new AtomicReference<>(Map.of());

    public ServiceJwtVerifier(
            ObjectMapper objectMapper,
            @Value("${onlinejudge.identity.jwt.issuer:onlinejudge.identity.v2}") String issuer,
            @Value("${onlinejudge.identity.service.course.audience:course}") String audience,
            @Value("${onlinejudge.identity.jwks.trust-bundle:}") String trustBundle
    ) {
        this.objectMapper = objectMapper;
        this.issuer = issuer;
        this.audience = audience;
        if (trustBundle != null && !trustBundle.isBlank()) {
            replace(trustBundle);
        }
    }

    /** Replaces the trusted key snapshot; a failed parse keeps the previous snapshot untouched. */
    public void replace(String jwksJson) {
        keys.set(parseJwks(jwksJson));
    }

    public Claims verify(String authorizationHeader) {
        try {
            String token = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                    ? authorizationHeader.substring(7).trim() : "";
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new Rejected("malformed token");
            }
            Map<String, Object> header = decode(parts[0]);
            if (!ALGORITHM.equals(stringClaim(header, "alg"))) {
                throw new Rejected("algorithm rejected");
            }
            PublicKey key = keys.get().get(stringClaim(header, "kid"));
            if (key == null) {
                throw new Rejected("unknown kid");
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new Rejected("signature rejected");
            }
            Map<String, Object> claims = decode(parts[1]);
            if (!issuer.equals(stringClaim(claims, "iss")) || !audience.equals(stringClaim(claims, "aud"))) {
                throw new Rejected("issuer or audience rejected");
            }
            long now = Instant.now().getEpochSecond();
            long issuedAt = numberClaim(claims, "iat");
            long expiresAt = numberClaim(claims, "exp");
            if (issuedAt > now + CLOCK_SKEW_SECONDS || expiresAt <= now - CLOCK_SKEW_SECONDS) {
                throw new Rejected("expired token");
            }
            return new Claims(stringClaim(claims, "sub"), stringListClaim(claims, "scopes"));
        } catch (Rejected rejected) {
            throw rejected;
        } catch (Exception exception) {
            throw new Rejected("malformed token", exception);
        }
    }

    private Map<String, Object> decode(String value) throws Exception {
        return objectMapper.readValue(Base64.getUrlDecoder().decode(value), new TypeReference<>() { });
    }

    private Map<String, PublicKey> parseJwks(String raw) {
        try {
            Object keysValue = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() { }).get("keys");
            if (!(keysValue instanceof List<?> keys) || keys.isEmpty()) {
                throw new IllegalArgumentException("keys missing");
            }
            Map<String, PublicKey> result = new LinkedHashMap<>();
            for (Object item : keys) {
                if (!(item instanceof Map<?, ?> key)
                        || !"RSA".equals(stringValue(key.get("kty")))
                        || !"sig".equals(stringValue(key.get("use")))
                        || !ALGORITHM.equals(stringValue(key.get("alg")))) {
                    throw new IllegalArgumentException("only RS256 signing JWKS keys are accepted");
                }
                result.put(stringValue(key.get("kid")), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        new BigInteger(1, Base64.getUrlDecoder().decode(stringValue(key.get("n")))),
                        new BigInteger(1, Base64.getUrlDecoder().decode(stringValue(key.get("e"))))
                )));
            }
            return Map.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWKS is invalid", exception);
        }
    }

    private static String stringClaim(Map<String, Object> values, String name) {
        return stringValue(values.get(name));
    }

    private static String stringValue(Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("required string claim is missing");
        }
        return string;
    }

    private static long numberClaim(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof Number number) || number.longValue() < 0) {
            throw new IllegalArgumentException("required numeric claim is missing");
        }
        return number.longValue();
    }

    private static List<String> stringListClaim(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("required list claim is missing");
        }
        return list.stream().map(ServiceJwtVerifier::stringValue).toList();
    }

    public record Claims(String subject, List<String> scopes) {
    }

    public static final class Rejected extends RuntimeException {
        public Rejected(String message) {
            super(message);
        }

        public Rejected(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
