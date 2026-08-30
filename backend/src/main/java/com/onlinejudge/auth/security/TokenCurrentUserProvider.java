package com.onlinejudge.auth.security;

import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.service.SessionTokenService;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.TreeSet;

@Component
public class TokenCurrentUserProvider implements CurrentUserProvider {
    private final SessionTokenService sessionTokenService;
    private final IdentityJwksCache identityJwksCache;
    private final SecurityVersionProjection securityVersions;

    public TokenCurrentUserProvider(
            SessionTokenService sessionTokenService,
            IdentityJwksCache identityJwksCache,
            SecurityVersionProjection securityVersions
    ) {
        this.sessionTokenService = sessionTokenService;
        this.identityJwksCache = identityJwksCache;
        this.securityVersions = securityVersions;
    }

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        Optional<String> token = bearerToken();
        if (token.isPresent()) {
            // The three-service deployment still routes login to this backend.
            // Keep its persisted opaque sessions valid on every protected route
            // until that complete login flow is cut over to Identity.  An
            // unrecognised bearer value is then handled strictly by the offline
            // JWT verifier; gateway X-User-* headers remain credentials never.
            return sessionTokenService.resolveCurrentUser(token.get())
                    .map(this::toCurrentUser)
                    .or(() -> identityJwksCache.verify(token.get(), securityVersions)
                            .flatMap(this::toCurrentUser));
        }
        return Optional.empty();
    }

    private CurrentUser toCurrentUser(AuthUserView user) {
        String primaryRole = user.roles().isEmpty() ? user.userType() : user.roles().get(0);
        return new CurrentUser(
                user.id(),
                user.username(),
                primaryRole,
                new TreeSet<>(user.roles()),
                new TreeSet<>(user.permissions())
        );
    }

    private Optional<CurrentUser> toCurrentUser(OfflineJwtVerifier.Principal principal) {
        try {
            long userId = Long.parseLong(principal.userId());
            if (userId <= 0 || principal.roles().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CurrentUser(
                    userId,
                    "",
                    principal.roles().get(0),
                    new TreeSet<>(principal.roles()),
                    new TreeSet<>(principal.permissions())
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
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
