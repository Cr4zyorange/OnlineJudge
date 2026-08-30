package com.onlinejudge.common.security;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public record CurrentUser(long id, String username, String role, Set<String> roles, Set<String> permissions) {
    public CurrentUser {
        username = username == null ? "" : username.trim();
        role = normalizeRole(role);
        roles = normalizeRoles(roles);
        if (roles.isEmpty() && !role.isEmpty()) {
            roles = Set.of(role);
        } else if (!roles.isEmpty() && !roles.contains(role)) {
            role = roles.iterator().next();
        }
        permissions = normalizePermissions(permissions);
    }

    public CurrentUser(long id, String username, String role, Set<String> permissions) {
        this(id, username, role, singletonRole(role), permissions);
    }

    public boolean hasRole(String expectedRole) {
        return expectedRole != null && roles.contains(normalizeRole(expectedRole));
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission.trim());
    }

    private static Set<String> normalizePermissions(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank()) {
                normalized.add(permission.trim());
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<String> singletonRole(String role) {
        String normalized = normalizeRole(role);
        return normalized.isEmpty() ? Set.of() : Set.of(normalized);
    }

    private static Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String role : roles) {
            String value = normalizeRole(role);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
