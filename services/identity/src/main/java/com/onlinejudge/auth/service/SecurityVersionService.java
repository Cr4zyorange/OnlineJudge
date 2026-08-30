package com.onlinejudge.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.repository.AuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Records the Identity-owned outbox fact; #337 delivers it with at-least-once semantics. */
@Service
public class SecurityVersionService {
    private final AuthRepository authRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public SecurityVersionService(AuthRepository authRepository, ObjectMapper objectMapper) {
        this(authRepository, objectMapper, Clock.systemDefaultZone());
    }

    SecurityVersionService(AuthRepository authRepository, ObjectMapper objectMapper, Clock clock) {
        this.authRepository = authRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public long advance(long userId, ChangeReason reason) {
        long nextVersion = authRepository.incrementSecurityVersion(userId);
        LocalDateTime occurredAt = LocalDateTime.now(clock);
        try {
            String correlationId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(Map.of(
                    "userId", String.valueOf(userId),
                    "securityVersion", nextVersion,
                    "changeReason", reason.name()
            ));
            authRepository.recordSecurityVersionOutbox(
                    UUID.randomUUID().toString(),
                    userId,
                    nextVersion,
                    occurredAt,
                    correlationId,
                    payload
            );
            return nextVersion;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist security-version outbox fact", exception);
        }
    }

    public enum ChangeReason {
        LOGOUT,
        PASSWORD_CHANGED,
        ROLE_CHANGED,
        PERMISSION_CHANGED,
        ACCOUNT_STATUS_CHANGED
    }
}
