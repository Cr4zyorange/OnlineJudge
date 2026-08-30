package com.onlinejudge.auth.domain;

public record PermissionView(
        long permissionId,
        String permissionCode,
        String permissionName,
        String permissionType,
        String moduleCode,
        String resourcePattern,
        boolean enabled
) {
}
