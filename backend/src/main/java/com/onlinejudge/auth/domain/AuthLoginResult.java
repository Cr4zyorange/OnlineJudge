package com.onlinejudge.auth.domain;

import java.time.LocalDateTime;

public record AuthLoginResult(String token, LocalDateTime expiresAt, AuthUserView user) {
}
