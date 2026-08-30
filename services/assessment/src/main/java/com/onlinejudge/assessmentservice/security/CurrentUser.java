package com.onlinejudge.assessmentservice.security;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record CurrentUser(String id, Set<String> roles, long securityVersion) {
    public CurrentUser { roles = roles.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet()); }
    public boolean hasRole(String role) { return roles.contains(role.toUpperCase(Locale.ROOT)); }
}
