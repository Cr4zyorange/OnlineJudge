package com.onlinejudge.auth.service;

import com.onlinejudge.auth.repository.AuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class SessionTokenService {
    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_MINUTES = 15;

    private final AuthRepository authRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public SessionTokenService(AuthRepository authRepository) {
        this(authRepository, Clock.systemDefaultZone());
    }

    SessionTokenService(AuthRepository authRepository, Clock clock) {
        this.authRepository = authRepository;
        this.clock = clock;
    }

    public SessionToken createSession(long userId) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String tokenId = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime issuedAt = LocalDateTime.now(clock);
        LocalDateTime expiresAt = issuedAt.plusMinutes(SESSION_MINUTES);
        long sessionId = authRepository.createSession(userId, digest(tokenId), issuedAt, expiresAt);
        return new SessionToken(sessionId, tokenId, issuedAt, expiresAt);
    }

    public void revokeTokenId(String tokenId) {
        authRepository.revokeSession(digest(tokenId), LocalDateTime.now(clock));
    }

    public String tokenId(String token) {
        return digest(token.trim());
    }

    private String digest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedToken = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedToken);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    public record SessionToken(long sessionId, String tokenId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
    }
}
