package com.onlinejudge.auth.controller;

public record RoleUpsertRequest(
        String roleCode,
        String roleName,
        String description,
        Boolean enabled
) {
}
