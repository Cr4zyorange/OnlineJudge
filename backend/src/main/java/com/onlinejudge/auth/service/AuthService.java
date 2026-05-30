package com.onlinejudge.auth.service;

import com.onlinejudge.auth.controller.LoginRequest;
import com.onlinejudge.auth.controller.PasswordChangeRequest;
import com.onlinejudge.auth.controller.ProfileUpdateRequest;
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
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final int MAX_FAILED_LOGIN_COUNT = 5;
    private static final int LOCK_MINUTES = 15;
    private static final int DISPLAY_NAME_MAX_LENGTH = 64;
    private static final int PHONE_MAX_LENGTH = 32;
    private static final int EMAIL_MAX_LENGTH = 128;
    private static final int AVATAR_URL_MAX_LENGTH = 255;
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

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
        requireText(request.username(), "账号不能为空");
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

    @Transactional(noRollbackFor = AuthApiException.class)
    public AuthLoginResult login(LoginRequest request) {
        String account = requireText(request.account(), "账号不能为空");
        String password = requireText(request.password(), "密码不能为空");
        LocalDateTime now = LocalDateTime.now();
        AuthUser user = authRepository.findUserByLoginIdentifier(account)
                .orElseThrow(() -> {
                    authAuditService.record(null, "LOGIN_FAILURE", "AUTH_USER", account, "FAILURE", "账号或密码错误");
                    return AuthApiException.loginFailed();
                });
        ensureLoginAllowed(user, now);
        if (!passwordSecurityService.matches(password, user.passwordHash(), user.passwordSalt())) {
            int baseFailedCount = user.lockedUntil() != null && !user.lockedUntil().isAfter(now)
                    ? 0
                    : user.failedLoginCount();
            int failedCount = baseFailedCount + 1;
            LocalDateTime lockedUntil = failedCount >= MAX_FAILED_LOGIN_COUNT ? now.plusMinutes(LOCK_MINUTES) : null;
            authRepository.updateFailedLogin(user.id(), failedCount, lockedUntil);
            if (lockedUntil != null) {
                authAuditService.record(user.id(), "ACCOUNT_LOCKED", "AUTH_USER", String.valueOf(user.id()), "SUCCESS", "登录失败次数过多");
            }
            authAuditService.record(user.id(), "LOGIN_FAILURE", "AUTH_USER", String.valueOf(user.id()), "FAILURE", "账号或密码错误");
            throw AuthApiException.loginFailed();
        }
        authRepository.resetLoginFailure(user.id(), now);
        SessionTokenService.SessionToken token = sessionTokenService.createSession(user.id());
        authAuditService.record(user.id(), "LOGIN_SUCCESS", "AUTH_USER", String.valueOf(user.id()), "SUCCESS", null);
        return new AuthLoginResult(token.token(), token.expiresAt(), authRepository.toUserView(user.id()));
    }

    public AuthUserView currentUser(long userId) {
        return authRepository.toUserView(userId);
    }

    @Transactional
    public AuthUserView updateProfile(long userId, ProfileUpdateRequest request) {
        AuthUser current = authRepository.findUserById(userId)
                .orElseThrow(() -> AuthApiException.notFound("用户不存在"));
        String displayName = requireText(request.displayName(), "显示名称不能为空");
        String phone = blankToNull(request.phone());
        String email = blankToNull(request.email());
        String avatarUrl = blankToNull(request.avatarUrl());
        validateProfileFields(displayName, phone, email, avatarUrl);
        authRepository.findUserByPhone(phone)
                .filter(user -> !user.id().equals(current.id()))
                .ifPresent(user -> {
                    throw AuthApiException.conflict("手机号已存在");
                });
        authRepository.findUserByEmail(email)
                .filter(user -> !user.id().equals(current.id()))
                .ifPresent(user -> {
                    throw AuthApiException.conflict("邮箱已存在");
                });
        authRepository.updateProfile(current.id(), displayName, phone, email, avatarUrl);
        return authRepository.toUserView(current.id());
    }

    @Transactional
    public void changePassword(long userId, PasswordChangeRequest request) {
        AuthUser current = authRepository.findUserById(userId)
                .orElseThrow(() -> AuthApiException.notFound("用户不存在"));
        String oldPassword = requireText(request.oldPassword(), "原密码不能为空");
        String newPassword = requireText(request.newPassword(), "新密码不能为空");
        if (!passwordSecurityService.matches(oldPassword, current.passwordHash(), current.passwordSalt())) {
            authAuditService.record(current.id(), "PASSWORD_CHANGE_FAILED", "AUTH_USER", String.valueOf(current.id()), "FAILURE", "原密码错误");
            throw AuthApiException.oldPasswordWrong();
        }
        if (oldPassword.equals(newPassword)) {
            throw AuthApiException.conflict("新旧密码不能相同");
        }
        PasswordSecurityService.PasswordCredential credential = passwordSecurityService.hash(newPassword);
        authRepository.updatePassword(current.id(), credential.passwordHash(), credential.passwordSalt());
        authRepository.revokeUserSessions(current.id(), LocalDateTime.now());
        authAuditService.record(current.id(), "PASSWORD_CHANGED", "AUTH_USER", String.valueOf(current.id()), "SUCCESS", null);
    }

    public void logout(long userId, String token) {
        sessionTokenService.revoke(token);
        authAuditService.record(userId, "LOGOUT", "AUTH_SESSION", null, "SUCCESS", null);
    }

    private void ensureLoginAllowed(AuthUser user, LocalDateTime now) {
        if (user.accountStatus() == AccountStatus.DISABLED || user.accountStatus() == AccountStatus.PENDING) {
            throw AuthApiException.disabled();
        }
        if (user.accountStatus() == AccountStatus.FROZEN) {
            throw AuthApiException.locked();
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
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

    private void validateProfileFields(String displayName, String phone, String email, String avatarUrl) {
        if (displayName.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw AuthApiException.badRequest("显示名称长度不能超过64个字符");
        }
        if (phone != null && (phone.length() > PHONE_MAX_LENGTH || !PHONE_PATTERN.matcher(phone).matches())) {
            throw AuthApiException.badRequest("手机号格式不正确");
        }
        if (email != null && (email.length() > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(email).matches())) {
            throw AuthApiException.badRequest("邮箱格式不正确");
        }
        if (avatarUrl != null && avatarUrl.length() > AVATAR_URL_MAX_LENGTH) {
            throw AuthApiException.badRequest("头像地址长度不能超过255个字符");
        }
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
