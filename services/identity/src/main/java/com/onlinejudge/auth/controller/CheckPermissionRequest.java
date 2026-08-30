package com.onlinejudge.auth.controller;

public record CheckPermissionRequest(
        String permissionCode,
        String resourceType,
        String resourceId
) {
}
