package com.onlinejudge.auth.controller;

public record RoleUpsertRequest(
        Long roleId,
        String roleCode,
        String roleName,
        String description,
        Boolean enabled
) {
}
