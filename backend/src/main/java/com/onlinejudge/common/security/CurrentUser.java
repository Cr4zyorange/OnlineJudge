package com.onlinejudge.common.security;

public record CurrentUser(Long id, String name, PlatformRole role) {
    public boolean isTeacher() {
        return role == PlatformRole.TEACHER || role == PlatformRole.ADMIN;
    }

    public boolean isAdmin() {
        return role == PlatformRole.ADMIN;
    }
}
