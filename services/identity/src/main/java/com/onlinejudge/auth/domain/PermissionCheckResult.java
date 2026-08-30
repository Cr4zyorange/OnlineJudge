package com.onlinejudge.auth.domain;

public record PermissionCheckResult(
        boolean allowed,
        String permissionCode,
        String resourceType,
        String resourceId,
        String reason
) {
}
