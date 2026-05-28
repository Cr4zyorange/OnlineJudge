package com.onlinejudge.auth.controller;

public record RegisterRequest(
        String username,
        String password,
        String userType,
        String displayName,
        String phone,
        String email,
        String avatarUrl
) {
}
