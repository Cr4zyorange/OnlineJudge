package com.onlinejudge.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class HeaderCurrentUserProvider implements CurrentUserProvider {
    @Override
    public Optional<CurrentUser> getCurrentUser() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        String rawUserId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        if (rawUserId == null || role == null || role.isBlank()) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(rawUserId.trim());
            if (userId <= 0) {
                return Optional.empty();
            }
            return Optional.of(new CurrentUser(
                    userId,
                    request.getHeader("X-Username"),
                    role,
                    parseCsv(request.getHeader("X-Permissions"))
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Set<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(permission -> !permission.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
