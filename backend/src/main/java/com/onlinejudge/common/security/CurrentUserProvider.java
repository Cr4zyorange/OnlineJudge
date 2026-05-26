package com.onlinejudge.common.security;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<CurrentUser> getCurrentUser();

    default CurrentUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new AuthenticationRequiredException("未登录或登录状态已失效"));
    }
}
