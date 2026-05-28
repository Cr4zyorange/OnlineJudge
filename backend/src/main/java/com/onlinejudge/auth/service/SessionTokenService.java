package com.onlinejudge.auth.service;

import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.repository.AuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class SessionTokenService {
    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_HOURS = 8;

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
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime issuedAt = LocalDateTime.now(clock);
        LocalDateTime expiresAt = issuedAt.plusHours(SESSION_HOURS);
        authRepository.createSession(userId, token, issuedAt, expiresAt);
        return new SessionToken(token, expiresAt);
    }

    public Optional<AuthUserView> resolveCurrentUser(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return authRepository.findValidSessionUser(token.trim(), LocalDateTime.now(clock));
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            authRepository.revokeSession(token.trim(), LocalDateTime.now(clock));
        }
    }

    public record SessionToken(String token, LocalDateTime expiresAt) {
    }
}
