package com.onlinejudge.auth.controller;

import java.util.List;

public record AdminCreateUserRequest(
        String username,
        String password,
        String userType,
        String displayName,
        String phone,
        String email,
        String avatarUrl,
        List<Long> roleIds
) {
}
