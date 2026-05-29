package com.onlinejudge.auth.domain;

import java.util.List;

public record RoleView(
        long roleId,
        String roleCode,
        String roleName,
        String description,
        boolean enabled,
        List<PermissionView> permissions
) {
}
