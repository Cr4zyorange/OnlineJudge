package com.onlinejudge.testsupport;

import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.service.SessionTokenService;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.security.CurrentUserProvider;
import com.onlinejudge.common.security.HeaderCurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.TreeSet;

/**
 * Test-scope bridge for legacy controller fixtures. Production business authentication is always
 * {@code OfflineJwtVerifier}; this class lets pre-v2 tests continue to exercise their persisted
 * opaque session fixtures, and only permits synthetic gateway headers when the test explicitly
 * enables that legacy fixture mode.
 */
final class LegacySessionOrHeaderCurrentUserProvider implements CurrentUserProvider {
    private final SessionTokenService sessionTokenService;
    private final HeaderCurrentUserProvider headerCurrentUserProvider;
    private final boolean allowHeaderAuth;

    LegacySessionOrHeaderCurrentUserProvider(
            SessionTokenService sessionTokenService,
            HeaderCurrentUserProvider headerCurrentUserProvider,
            boolean allowHeaderAuth
    ) {
        this.sessionTokenService = sessionTokenService;
        this.headerCurrentUserProvider = headerCurrentUserProvider;
        this.allowHeaderAuth = allowHeaderAuth;
    }

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        return bearerToken()
                .flatMap(sessionTokenService::resolveCurrentUser)
                .map(this::toCurrentUser)
                .or(() -> allowHeaderAuth ? headerCurrentUserProvider.getCurrentUser() : Optional.empty());
    }

    private CurrentUser toCurrentUser(AuthUserView user) {
        String primaryRole = user.roles() == null || user.roles().isEmpty()
                ? user.userType()
                : user.roles().get(0);
        return new CurrentUser(
                user.id(),
                user.username(),
                primaryRole,
                new TreeSet<>(user.permissions() == null ? java.util.Set.of() : user.permissions())
        );
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
}
