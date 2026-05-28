package com.onlinejudge.auth.service;

import com.onlinejudge.auth.controller.LoginRequest;
import com.onlinejudge.auth.controller.RegisterRequest;
import com.onlinejudge.auth.domain.AccountStatus;
import com.onlinejudge.auth.domain.AuthLoginResult;
import com.onlinejudge.auth.domain.AuthUser;
import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.exception.AuthApiException;
import com.onlinejudge.auth.repository.AuthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {
    private static final int MAX_FAILED_LOGIN_COUNT = 5;

    private final AuthRepository authRepository;
    private final PasswordSecurityService passwordSecurityService;
    private final SessionTokenService sessionTokenService;

    public AuthService(
            AuthRepository authRepository,
            PasswordSecurityService passwordSecurityService,
            SessionTokenService sessionTokenService
    ) {
        this.authRepository = authRepository;
        this.passwordSecurityService = passwordSecurityService;
        this.sessionTokenService = sessionTokenService;
    }

    @Transactional
    public AuthUserView register(RegisterRequest request) {
        String username = requireText(request.username(), "账号不能为空");
        String userType = normalizeRole(request.userType());
        String displayName = requireText(request.displayName(), "显示名称不能为空");
        if (authRepository.findUserByUsername(username).isPresent()) {
            throw AuthApiException.conflict("账号已存在");
        }
        authRepository.ensureBaseRolesAndPermissions();
        PasswordSecurityService.PasswordCredential credential = passwordSecurityService.hash(request.password());
        long userId = authRepository.createUser(
                username,
                userType,
                displayName,
                blankToNull(request.phone()),
                blankToNull(request.email()),
                blankToNull(request.avatarUrl()),
                credential.passwordHash(),
                credential.passwordSalt()
        );
        authRepository.assignRole(userId, userType, null);
        return authRepository.toUserView(userId);
    }

    @Transactional
    public AuthLoginResult login(LoginRequest request) {
        String account = requireText(request.account(), "账号不能为空");
        String password = requireText(request.password(), "密码不能为空");
        AuthUser user = authRepository.findUserByUsername(account)
                .or(() -> authRepository.findUserByEmail(account))
                .or(() -> authRepository.findUserByPhone(account))
                .orElseThrow(AuthApiException::loginFailed);
        ensureLoginAllowed(user);
        if (!passwordSecurityService.matches(password, user.passwordHash(), user.passwordSalt())) {
            int failedCount = user.failedLoginCount() + 1;
            authRepository.updateFailedLogin(user.id(), failedCount);
            if (failedCount >= MAX_FAILED_LOGIN_COUNT) {
                authRepository.updateAccountStatus(user.id(), AccountStatus.FROZEN);
            }
            throw AuthApiException.loginFailed();
        }
        authRepository.resetLoginFailure(user.id(), LocalDateTime.now());
        SessionTokenService.SessionToken token = sessionTokenService.createSession(user.id());
        return new AuthLoginResult(token.token(), token.expiresAt(), authRepository.toUserView(user.id()));
    }

    public AuthUserView currentUser(long userId) {
        return authRepository.toUserView(userId);
    }

    public void logout(String token) {
        sessionTokenService.revoke(token);
    }

    private void ensureLoginAllowed(AuthUser user) {
        if (user.accountStatus() == AccountStatus.DISABLED || user.accountStatus() == AccountStatus.PENDING) {
            throw AuthApiException.disabled();
        }
        if (user.accountStatus() == AccountStatus.FROZEN) {
            throw AuthApiException.locked();
        }
    }

    private String normalizeRole(String role) {
        String normalized = requireText(role, "用户类型不能为空").toUpperCase(Locale.ROOT);
        if (!normalized.equals("STUDENT") && !normalized.equals("TEACHER") && !normalized.equals("ADMIN")) {
            throw AuthApiException.badRequest("用户类型必须是 STUDENT、TEACHER 或 ADMIN");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw AuthApiException.badRequest(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
