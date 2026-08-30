package com.onlinejudge.courseservice.security;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record CurrentUser(long id, Set<String> roles, Set<String> permissions, long securityVersion) {
    public CurrentUser {
        roles = normalized(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasRole(String role) {
        return role != null && roles.contains(role.trim().toUpperCase(Locale.ROOT));
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission.trim());
    }

    private static Set<String> normalized(Set<String> source) {
        if (source == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String role : source) {
            if (role != null && !role.isBlank()) {
                result.add(role.trim().toUpperCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }
}
