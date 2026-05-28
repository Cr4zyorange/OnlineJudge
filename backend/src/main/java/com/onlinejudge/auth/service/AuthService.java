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
    private final AuthAuditService authAuditService;

    public AuthService(
            AuthRepository authRepository,
            PasswordSecurityService passwordSecurityService,
            SessionTokenService sessionTokenService,
            AuthAuditService authAuditService
    ) {
        this.authRepository = authRepository;
        this.passwordSecurityService = passwordSecurityService;
        this.sessionTokenService = sessionTokenService;
        this.authAuditService = authAuditService;
    }

    @Transactional
    public AuthUserView register(RegisterRequest request) {
        String username = requireText(request.username(), "账号不能为空");
        String userType = normalizeRole(request.userType());
        if (!"STUDENT".equals(userType)) {
            throw AuthApiException.badRequest("公开注册仅支持学生账号");
        }
        return registerTrusted(request, userType);
    }

    @Transactional
    public AuthUserView registerTrusted(RegisterRequest request, String trustedUserType) {
        String username = requireText(request.username(), "账号不能为空");
        String userType = normalizeRole(trustedUserType);
        String displayName = requireText(request.displayName(), "显示名称不能为空");
        if (authRepository.findUserByUsername(username).isPresent()) {
            throw AuthApiException.conflict("账号已存在");
        }
        String phone = blankToNull(request.phone());
        String email = blankToNull(request.email());
        if (authRepository.findUserByEmail(email).isPresent()) {
            throw AuthApiException.conflict("邮箱已存在");
        }
        if (authRepository.findUserByPhone(phone).isPresent()) {
            throw AuthApiException.conflict("手机号已存在");
        }
        authRepository.ensureBaseRolesAndPermissions();
        PasswordSecurityService.PasswordCredential credential = passwordSecurityService.hash(request.password());
        long userId = authRepository.createUser(
                username,
                userType,
                displayName,
                phone,
                email,
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
        AuthUser user = authRepository.findUserByLoginIdentifier(account)
                .orElseThrow(() -> {
                    authAuditService.record(null, "LOGIN_FAILURE", "AUTH_USER", account, "FAILURE", "账号或密码错误");
                    return AuthApiException.loginFailed();
                });
        ensureLoginAllowed(user);
        if (!passwordSecurityService.matches(password, user.passwordHash(), user.passwordSalt())) {
            int failedCount = user.failedLoginCount() + 1;
            authRepository.updateFailedLogin(user.id(), failedCount);
            if (failedCount >= MAX_FAILED_LOGIN_COUNT) {
                authRepository.updateAccountStatus(user.id(), AccountStatus.FROZEN);
                authAuditService.record(user.id(), "ACCOUNT_LOCKED", "AUTH_USER", String.valueOf(user.id()), "SUCCESS", "登录失败次数过多");
            }
            authAuditService.record(user.id(), "LOGIN_FAILURE", "AUTH_USER", String.valueOf(user.id()), "FAILURE", "账号或密码错误");
            throw AuthApiException.loginFailed();
        }
        authRepository.resetLoginFailure(user.id(), LocalDateTime.now());
        SessionTokenService.SessionToken token = sessionTokenService.createSession(user.id());
        authAuditService.record(user.id(), "LOGIN_SUCCESS", "AUTH_USER", String.valueOf(user.id()), "SUCCESS", null);
        return new AuthLoginResult(token.token(), token.expiresAt(), authRepository.toUserView(user.id()));
    }

    public AuthUserView currentUser(long userId) {
        return authRepository.toUserView(userId);
    }

    public void logout(long userId, String token) {
        sessionTokenService.revoke(token);
        authAuditService.record(userId, "LOGOUT", "AUTH_SESSION", null, "SUCCESS", null);
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
