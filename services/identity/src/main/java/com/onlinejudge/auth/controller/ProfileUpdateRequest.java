package com.onlinejudge.auth.controller;

public record ProfileUpdateRequest(
        String displayName,
        String phone,
        String email,
        String avatarUrl
) {
}
