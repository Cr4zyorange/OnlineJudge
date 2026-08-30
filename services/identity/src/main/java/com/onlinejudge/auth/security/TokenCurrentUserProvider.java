package com.onlinejudge.auth.security;

import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.repository.AuthRepository;
import com.onlinejudge.auth.service.SessionTokenService;
import com.onlinejudge.auth.exception.AuthApiException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.security.CurrentUserProvider;
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
    private final JwtTokenService jwtTokenService;
    private final AuthRepository authRepository;
    private final SessionTokenService sessionTokenService;

    public TokenCurrentUserProvider(
            JwtTokenService jwtTokenService,
            AuthRepository authRepository,
            SessionTokenService sessionTokenService
    ) {
        this.jwtTokenService = jwtTokenService;
        this.authRepository = authRepository;
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        Optional<JwtTokenService.ValidatedUserToken> token = bearerToken().flatMap(jwtTokenService::validateUserToken);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        JwtTokenService.ValidatedUserToken value = token.get();
        String sessionTokenId = sessionTokenService.tokenId(value.tokenId());
        Optional<AuthUserView> user = authRepository.findValidJwtSessionUser(
                value.sessionId(),
                sessionTokenId,
                value.userId(),
                value.securityVersion(),
                java.time.LocalDateTime.now()
        );
        if (user.isPresent()) {
            return user.map(this::toCurrentUser);
        }
        authRepository.findSessionUser(sessionTokenId)
                .filter(subject -> !"ACTIVE".equals(subject.accountStatus()))
                .ifPresent(subject -> {
                    throw AuthApiException.disabled();
                });
        return Optional.empty();
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
}
