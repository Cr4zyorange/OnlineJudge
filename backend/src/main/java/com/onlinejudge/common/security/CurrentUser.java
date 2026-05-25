package com.onlinejudge.common.security;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public record CurrentUser(long id, String username, String role, Set<String> permissions) {
    public CurrentUser {
        username = username == null ? "" : username.trim();
        role = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        permissions = normalizePermissions(permissions);
    }

    public boolean hasRole(String expectedRole) {
        return expectedRole != null && role.equals(expectedRole.trim().toUpperCase(Locale.ROOT));
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
}
