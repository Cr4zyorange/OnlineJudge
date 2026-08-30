package com.onlinejudge.auth.controller;

import java.time.Instant;

public record ServiceTokenResponse(String accessToken, Instant expiresAt, String audience) {
}
