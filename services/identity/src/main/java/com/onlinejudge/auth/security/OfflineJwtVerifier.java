package com.onlinejudge.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Protocol-library verifier deliberately has no HTTP client and no Identity repository dependency.
 * A business service refreshes this immutable JWKS snapshot on its own bounded schedule, while each
 * request verifies only against the already cached keys and the local security-version projection.
 */
public final class OfflineJwtVerifier {
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Map<String, PublicKey> publicKeys;

    private OfflineJwtVerifier(
            ObjectMapper objectMapper,
            Clock clock,
            String issuer,
            String audience,
            Map<String, PublicKey> publicKeys
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        this.publicKeys = Map.copyOf(publicKeys);
    }

    public static OfflineJwtVerifier fromJwks(
            ObjectMapper objectMapper,
            Clock clock,
            String issuer,
            String audience,
            Map<String, Object> jwks
    ) {
        return new OfflineJwtVerifier(objectMapper, clock, issuer, audience, parseJwks(jwks));
    }

    public OfflineJwtVerifier withRefreshedJwks(Map<String, Object> jwks) {
        return new OfflineJwtVerifier(objectMapper, clock, issuer, audience, parseJwks(jwks));
    }

    public Verification verify(String token, SecurityVersionLookup securityVersions) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3) {
                return rejected(Rejection.MALFORMED);
            }
            Map<String, Object> header = decodeMap(parts[0]);
            if (!JwtTokenService.ALGORITHM.equals(stringClaim(header, "alg"))) {
                return rejected(Rejection.ALGORITHM_NOT_ALLOWED);
            }
            PublicKey key = publicKeys.get(stringClaim(header, "kid"));
            if (key == null) {
                return rejected(Rejection.UNKNOWN_KID);
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                return rejected(Rejection.SIGNATURE_INVALID);
            }

            Map<String, Object> claims = decodeMap(parts[1]);
            if (!issuer.equals(stringClaim(claims, "iss"))) {
                return rejected(Rejection.ISSUER_MISMATCH);
            }
            if (!audience.equals(stringClaim(claims, "aud"))) {
                return rejected(Rejection.AUDIENCE_MISMATCH);
            }
            long now = clock.instant().getEpochSecond();
            long issuedAt = numberClaim(claims, "iat");
            long expiresAt = numberClaim(claims, "exp");
            if (issuedAt > now + CLOCK_SKEW_SECONDS) {
                return rejected(Rejection.ISSUED_IN_FUTURE);
            }
            if (expiresAt <= now - CLOCK_SKEW_SECONDS) {
                return rejected(Rejection.EXPIRED);
            }
            String userId = stringClaim(claims, "userId");
            long securityVersion = numberClaim(claims, "securityVersion");
            if (securityVersion < securityVersions.minimumAcceptedVersion(userId)) {
                return rejected(Rejection.SECURITY_VERSION_STALE);
            }
            return accepted(new Principal(
                    userId,
                    stringClaim(claims, "sessionId"),
                    securityVersion,
                    stringListClaim(claims, "roles"),
                    stringListClaim(claims, "permissions"),
                    Instant.ofEpochSecond(issuedAt),
                    Instant.ofEpochSecond(expiresAt)
            ));
        } catch (IllegalArgumentException exception) {
            return rejected(Rejection.MALFORMED);
        } catch (Exception exception) {
            return rejected(Rejection.SIGNATURE_INVALID);
        }
    }

    private Verification accepted(Principal principal) {
        return new Verification(true, null, principal);
    }

    private Verification rejected(Rejection rejection) {
        return new Verification(false, rejection, null);
    }

    private Map<String, Object> decodeMap(String value) {
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(value), new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT JSON is invalid", exception);
        }
    }

    private static Map<String, PublicKey> parseJwks(Map<String, Object> jwks) {
        Object keys = jwks == null ? null : jwks.get("keys");
        if (!(keys instanceof List<?> keyList) || keyList.isEmpty()) {
            throw new IllegalArgumentException("JWKS keys are required");
        }
        Map<String, PublicKey> result = new LinkedHashMap<>();
        for (Object item : keyList) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("JWKS key is invalid");
            }
            String kid = stringValue(raw.get("kid"));
            if (!"RSA".equals(stringValue(raw.get("kty")))
                    || !"sig".equals(stringValue(raw.get("use")))
                    || !JwtTokenService.ALGORITHM.equals(stringValue(raw.get("alg")))) {
                throw new IllegalArgumentException("JWKS key does not meet the Identity RS256 contract");
            }
            result.put(kid, rsaPublicKey(stringValue(raw.get("n")), stringValue(raw.get("e"))));
        }
        return result;
    }

    private static PublicKey rsaPublicKey(String modulus, String exponent) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, Base64.getUrlDecoder().decode(modulus)),
                    new BigInteger(1, Base64.getUrlDecoder().decode(exponent))
            ));
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWKS RSA key is invalid", exception);
        }
    }

    private static String stringClaim(Map<String, Object> values, String key) {
        return stringValue(values.get(key));
    }

    private static String stringValue(Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("required string value is missing");
        }
        return string;
    }

    private static long numberClaim(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number) || number.longValue() < 0) {
            throw new IllegalArgumentException("required number value is missing");
        }
        return number.longValue();
    }

    private static List<String> stringListClaim(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("required list value is missing");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(stringValue(item));
        }
        return List.copyOf(result);
    }

    @FunctionalInterface
    public interface SecurityVersionLookup {
        long minimumAcceptedVersion(String userId);
    }

    public enum Rejection {
        MALFORMED,
        ALGORITHM_NOT_ALLOWED,
        UNKNOWN_KID,
        SIGNATURE_INVALID,
        ISSUER_MISMATCH,
        AUDIENCE_MISMATCH,
        ISSUED_IN_FUTURE,
        EXPIRED,
        SECURITY_VERSION_STALE
    }

    public record Verification(boolean accepted, Rejection rejection, Principal principal) {
    }

    public record Principal(
            String userId,
            String sessionId,
            long securityVersion,
            List<String> roles,
            List<String> permissions,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
