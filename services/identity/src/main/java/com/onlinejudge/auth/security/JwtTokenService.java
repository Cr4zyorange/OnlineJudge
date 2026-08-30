package com.onlinejudge.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.domain.AuthUser;
import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.service.SessionTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Identity issuer's only token implementation. Consumers use the public JWKS representation,
 * not an Identity HTTP call, for request-time verification.
 */
@Service
public class JwtTokenService {
    public static final String ISSUER = "onlinejudge.identity.v2";
    public static final String USER_AUDIENCE = "onlinejudge.api";
    public static final String ALGORITHM = "RS256";
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final ObjectMapper objectMapper;
    private final SessionTokenService sessionTokenService;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Map<String, PublicKey> verificationKeys = new ConcurrentHashMap<>();
    private volatile SigningKey activeSigningKey;

    @Autowired
    public JwtTokenService(
            ObjectMapper objectMapper,
            SessionTokenService sessionTokenService,
            @Value("${onlinejudge.identity.jwt.issuer:" + ISSUER + "}") String issuer,
            @Value("${onlinejudge.identity.jwt.audience:" + USER_AUDIENCE + "}") String audience,
            @Value("${onlinejudge.identity.jwt.current-kid:identity-dev-1}") String kid,
            @Value("${onlinejudge.identity.jwt.current-private-key:}") String encodedPrivateKey,
            @Value("${onlinejudge.identity.jwt.previous-public-keys:}") String previousPublicKeys,
            @Value("${onlinejudge.identity.jwt.allow-ephemeral-keys:true}") boolean allowEphemeralKeys
    ) {
        this(objectMapper, sessionTokenService, Clock.systemUTC(), issuer, audience,
                initialSigningKey(kid, encodedPrivateKey, allowEphemeralKeys), previousPublicKeys);
    }

    JwtTokenService(
            ObjectMapper objectMapper,
            SessionTokenService sessionTokenService,
            Clock clock,
            String issuer,
            String audience,
            SigningKey initialSigningKey,
            String previousPublicKeys
    ) {
        this.objectMapper = objectMapper;
        this.sessionTokenService = sessionTokenService;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        this.activeSigningKey = initialSigningKey;
        verificationKeys.put(initialSigningKey.kid(), initialSigningKey.publicKey());
        loadPreviousPublicKeys(previousPublicKeys);
    }

    public IssuedUserToken issueUserToken(AuthUser user, AuthUserView userView) {
        SessionTokenService.SessionToken session = sessionTokenService.createSession(user.id());
        Instant issuedAt = session.issuedAt().atZone(ZoneId.systemDefault()).toInstant();
        Instant expiresAt = session.expiresAt().atZone(ZoneId.systemDefault()).toInstant();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", String.valueOf(user.id()));
        claims.put("roles", userView.roles());
        claims.put("permissions", userView.permissions());
        claims.put("sessionId", String.valueOf(session.sessionId()));
        claims.put("securityVersion", user.securityVersion());
        claims.put("jti", session.tokenId());
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("iss", issuer);
        claims.put("aud", audience);
        return new IssuedUserToken(sign(claims), session.expiresAt());
    }

    public Optional<ValidatedUserToken> validateUserToken(String token) {
        try {
            ParsedJwt parsed = parseAndVerify(token);
            Map<String, Object> claims = parsed.claims();
            long now = clock.instant().getEpochSecond();
            long issuedAt = requiredLong(claims, "iat");
            long expiresAt = requiredLong(claims, "exp");
            if (issuedAt > now + CLOCK_SKEW_SECONDS || expiresAt <= now - CLOCK_SKEW_SECONDS) {
                return Optional.empty();
            }
            if (!issuer.equals(requiredString(claims, "iss")) || !audience.equals(requiredString(claims, "aud"))) {
                return Optional.empty();
            }
            return Optional.of(new ValidatedUserToken(
                    Long.parseLong(requiredString(claims, "userId")),
                    Long.parseLong(requiredString(claims, "sessionId")),
                    requiredString(claims, "jti"),
                    requiredLong(claims, "securityVersion"),
                    requiredStringList(claims, "roles"),
                    requiredStringList(claims, "permissions"),
                    Instant.ofEpochSecond(issuedAt),
                    Instant.ofEpochSecond(expiresAt)
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Service credentials are distinct from user sessions and bind one workload to one audience. */
    public IssuedServiceToken issueServiceToken(String workloadSubject, String audience, List<String> scopes, Duration ttl) {
        if (workloadSubject == null || workloadSubject.isBlank() || audience == null || audience.isBlank()
                || scopes == null || scopes.isEmpty() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("service token claims are invalid");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", workloadSubject);
        claims.put("scopes", List.copyOf(scopes));
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("iss", issuer);
        claims.put("aud", audience);
        return new IssuedServiceToken(sign(claims), expiresAt);
    }

    public Map<String, Object> jwks() {
        List<Map<String, String>> keys = verificationKeys.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toJwk(entry.getKey(), entry.getValue()))
                .toList();
        return Map.of("keys", keys);
    }

    /** Keeps the former public key available during the maximum token lifetime/cache overlap. */
    public synchronized void rotate(SigningKey nextKey) {
        Objects.requireNonNull(nextKey, "nextKey");
        verificationKeys.put(activeSigningKey.kid(), activeSigningKey.publicKey());
        activeSigningKey = nextKey;
        verificationKeys.put(nextKey.kid(), nextKey.publicKey());
    }

    public static SigningKey generatedSigningKey(String kid) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new SigningKey(kid, pair.getPrivate(), pair.getPublic());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate RSA signing key", exception);
        }
    }

    private String sign(Map<String, Object> claims) {
        try {
            SigningKey key = activeSigningKey;
            String header = base64Url(objectMapper.writeValueAsBytes(Map.of(
                    "alg", ALGORITHM,
                    "typ", "JWT",
                    "kid", key.kid()
            )));
            String payload = base64Url(objectMapper.writeValueAsBytes(claims));
            String unsigned = header + "." + payload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key.privateKey());
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + base64Url(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private ParsedJwt parseAndVerify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT is required");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT must have three parts");
        }
        Map<String, Object> header = readMap(parts[0]);
        if (!ALGORITHM.equals(requiredString(header, "alg"))) {
            throw new IllegalArgumentException("JWT algorithm is not allowed");
        }
        PublicKey key = verificationKeys.get(requiredString(header, "kid"));
        if (key == null) {
            throw new IllegalArgumentException("Unknown JWT kid");
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new IllegalArgumentException("JWT signature is invalid");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT signature is invalid", exception);
        }
        return new ParsedJwt(header, readMap(parts[1]));
    }

    private void loadPreviousPublicKeys(String configuredKeys) {
        if (configuredKeys == null || configuredKeys.isBlank()) {
            return;
        }
        for (String entry : configuredKeys.split(",")) {
            String[] pieces = entry.trim().split(":", 2);
            if (pieces.length != 2 || pieces[0].isBlank() || pieces[1].isBlank()) {
                throw new IllegalStateException("Each previous public key must be kid:base64-x509");
            }
            verificationKeys.put(pieces[0], readPublicKey(pieces[1]));
        }
    }

    private Map<String, Object> readMap(String encoded) {
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(encoded), new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT JSON is invalid", exception);
        }
    }

    private static SigningKey initialSigningKey(String kid, String encodedPrivateKey, boolean allowEphemeralKeys) {
        if (kid == null || kid.isBlank()) {
            throw new IllegalStateException("Identity JWT kid must not be blank");
        }
        if (encodedPrivateKey == null || encodedPrivateKey.isBlank()) {
            if (!allowEphemeralKeys) {
                throw new IllegalStateException("IDENTITY_JWT_SIGNING_KEY is required when ephemeral keys are disabled");
            }
            return generatedSigningKey(kid);
        }
        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encodedPrivateKey)));
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            // A PKCS#8 RSA private key exposes no portable public-key accessor. Derive it from CRT form.
            java.security.interfaces.RSAPrivateCrtKey rsa = (java.security.interfaces.RSAPrivateCrtKey) privateKey;
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                    new java.security.spec.RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
            return new SigningKey(kid, privateKey, publicKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Identity JWT signing key must be base64 PKCS#8 RSA", exception);
        }
    }

    private static PublicKey readPublicKey(String encoded) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (Exception exception) {
            throw new IllegalStateException("Previous Identity public key must be base64 X.509 RSA", exception);
        }
    }

    private static Map<String, String> toJwk(String kid, PublicKey publicKey) {
        RSAPublicKey rsa = (RSAPublicKey) publicKey;
        return Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", ALGORITHM,
                "kid", kid,
                "n", base64Url(unsigned(rsa.getModulus())),
                "e", base64Url(unsigned(rsa.getPublicExponent()))
        );
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] unsigned = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
            return unsigned;
        }
        return bytes;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("JWT claim " + key + " is required");
        }
        return string;
    }

    private static long requiredLong(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number) || number.longValue() < 0) {
            throw new IllegalArgumentException("JWT claim " + key + " is required");
        }
        return number.longValue();
    }

    private static List<String> requiredStringList(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?> valuesList)) {
            throw new IllegalArgumentException("JWT claim " + key + " is required");
        }
        List<String> result = new ArrayList<>();
        for (Object item : valuesList) {
            if (!(item instanceof String string) || string.isBlank()) {
                throw new IllegalArgumentException("JWT claim " + key + " is invalid");
            }
            result.add(string);
        }
        return List.copyOf(result);
    }

    private record ParsedJwt(Map<String, Object> header, Map<String, Object> claims) {
    }

    public record SigningKey(String kid, PrivateKey privateKey, PublicKey publicKey) {
        public SigningKey {
            if (kid == null || kid.isBlank() || privateKey == null || publicKey == null) {
                throw new IllegalArgumentException("A signing key needs kid, private key and public key");
            }
        }
    }

    public record IssuedUserToken(String token, LocalDateTime expiresAt) {
    }

    public record IssuedServiceToken(String token, Instant expiresAt) {
    }

    public record ValidatedUserToken(
            long userId,
            long sessionId,
            String tokenId,
            long securityVersion,
            List<String> roles,
            List<String> permissions,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
