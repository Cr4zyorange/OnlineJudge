package com.onlinejudge.auth.security;

import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.service.SessionTokenService;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.security.CurrentUserProvider;
import com.onlinejudge.common.security.HeaderCurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.TreeSet;

@Primary
@Component
public class TokenCurrentUserProvider implements CurrentUserProvider {
    private final SessionTokenService sessionTokenService;
    private final HeaderCurrentUserProvider headerCurrentUserProvider;

    public TokenCurrentUserProvider(
            SessionTokenService sessionTokenService,
            HeaderCurrentUserProvider headerCurrentUserProvider
    ) {
        this.sessionTokenService = sessionTokenService;
        this.headerCurrentUserProvider = headerCurrentUserProvider;
    }

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        Optional<String> token = bearerToken();
        if (token.isPresent()) {
            return sessionTokenService.resolveCurrentUser(token.get())
                    .map(this::toCurrentUser);
        }
        if (isAuthSessionEndpoint()) {
            return Optional.empty();
        }
        return headerCurrentUserProvider.getCurrentUser();
    }

    private CurrentUser toCurrentUser(AuthUserView user) {
        String primaryRole = user.roles().isEmpty() ? user.userType() : user.roles().get(0);
        return new CurrentUser(user.id(), user.username(), primaryRole, new TreeSet<>(user.permissions()));
    }

    private Optional<String> bearerToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private boolean isAuthSessionEndpoint() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        String uri = attributes.getRequest().getRequestURI();
        return "/api/v1/auth/me".equals(uri) || "/api/v1/auth/logout".equals(uri);
    }
}
