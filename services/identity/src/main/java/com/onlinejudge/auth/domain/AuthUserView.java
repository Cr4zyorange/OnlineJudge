package com.onlinejudge.auth.domain;

import java.util.List;

public record AuthUserView(
        Long id,
        String username,
        String userType,
        String displayName,
        String phone,
        String email,
        String avatarUrl,
        String accountStatus,
        List<String> roles,
        List<String> permissions
) {
}
