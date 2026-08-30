package com.onlinejudge.auth.domain;

import java.time.LocalDateTime;

public record AuthUser(
        Long id,
        String username,
        String userType,
        String displayName,
        String phone,
        String email,
        String avatarUrl,
        String passwordHash,
        String passwordSalt,
        AccountStatus accountStatus,
        long securityVersion,
        int failedLoginCount,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt
) {
}
