package com.onlinejudge.auth.service;

import com.onlinejudge.auth.controller.AdminCreateUserRequest;
import com.onlinejudge.auth.controller.RegisterRequest;
import com.onlinejudge.auth.controller.RoleUpsertRequest;
import com.onlinejudge.auth.domain.AccountStatus;
import com.onlinejudge.auth.domain.AuthAuditLogView;
import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.domain.PageResult;
import com.onlinejudge.auth.domain.PermissionView;
import com.onlinejudge.auth.domain.RoleView;
import com.onlinejudge.auth.exception.AuthApiException;
import com.onlinejudge.auth.repository.AuthRepository;
import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class RoleService {
    private final AuthRepository authRepository;
    private final AuthAuditService authAuditService;
    private final AuthService authService;
    private final SecurityVersionService securityVersionService;

    public RoleService(
            AuthRepository authRepository,
            AuthAuditService authAuditService,
            AuthService authService,
            SecurityVersionService securityVersionService
    ) {
        this.authRepository = authRepository;
        this.authAuditService = authAuditService;
        this.authService = authService;
        this.securityVersionService = securityVersionService;
    }

    public PageResult<AuthUserView> listUsers(CurrentUser currentUser, String keyword, String role, String status, int page, int size) {
        requireAdmin(currentUser);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        return new PageResult<>(
                authRepository.listUsers(keyword, role, status, normalizedPage, normalizedSize),
                authRepository.countUsers(keyword, role, status)
        );
    }

    public List<RoleView> listRoles(CurrentUser currentUser) {
        requireAdmin(currentUser);
        return authRepository.listRoles();
    }

    public List<PermissionView> listPermissions(CurrentUser currentUser) {
        requireAdmin(currentUser);
        return authRepository.listPermissions();
    }

    public PageResult<AuthAuditLogView> listAuditLogs(
            CurrentUser currentUser,
            Long operatorId,
            String operationType,
            String resultStatus,
            String startTime,
            String endTime,
            int page,
            int size
    ) {
        requireAdmin(currentUser);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        LocalDateTime start = parseTime(startTime, "startTime");
        LocalDateTime end = parseTime(endTime, "endTime");
        return new PageResult<>(
                authRepository.listAuditLogs(operatorId, operationType, resultStatus, start, end, normalizedPage, normalizedSize),
                authRepository.countAuditLogs(operatorId, operationType, resultStatus, start, end)
        );
    }

    @Transactional
    public AuthUserView createAdminUser(CurrentUser currentUser, AdminCreateUserRequest request) {
        requireAdmin(currentUser);
        AuthUserView user = authService.registerTrusted(new RegisterRequest(
                request.username(),
                request.password(),
                request.userType(),
                request.displayName(),
                request.phone(),
                request.email(),
                request.avatarUrl()
        ), request.userType());
        if (request.roleIds() == null || request.roleIds().isEmpty()) {
            authAuditService.record(currentUser.id(), "USER_CREATED", "AUTH_USER", String.valueOf(user.id()), "SUCCESS", null);
            return user;
        }
        AuthUserView updatedUser = updateUserRoles(currentUser, user.id(), request.roleIds());
        authAuditService.record(currentUser.id(), "USER_CREATED", "AUTH_USER", String.valueOf(user.id()), "SUCCESS", null);
        return updatedUser;
    }

    @Transactional
    public RoleView createRole(CurrentUser currentUser, RoleUpsertRequest request) {
        requireAdmin(currentUser);
        long roleId = authRepository.createRole(
                normalizeCode(requireText(request.roleCode(), "角色编码不能为空")),
                requireText(request.roleName(), "角色名称不能为空"),
                blankToNull(request.description()),
                request.enabled() == null || request.enabled()
        );
        return authRepository.roleView(roleId);
    }

    @Transactional
    public RoleView updateRole(CurrentUser currentUser, RoleUpsertRequest request) {
        requireAdmin(currentUser);
        long roleId = requireRoleId(request.roleId());
        if (!authRepository.roleExists(roleId)) {
            throw AuthApiException.notFound("角色不存在");
        }
        RoleView existing = authRepository.roleView(roleId);
        String roleCode = normalizeCode(requireText(request.roleCode(), "角色编码不能为空"));
        boolean enabled = request.enabled() == null || request.enabled();
        authRepository.updateRole(
                roleId,
                roleCode,
                requireText(request.roleName(), "角色名称不能为空"),
                blankToNull(request.description()),
                enabled
        );
        // Role code or enabled-state changes alter the claims authorized by every assigned user.
        // Advance and outbox each user inside this transaction before any stale JWT can be accepted
        // by an offline consumer that has consumed the corresponding v2 projection event.
        if (!existing.roleCode().equals(roleCode) || existing.enabled() != enabled) {
            for (Long userId : authRepository.userIdsForRole(roleId)) {
                securityVersionService.advance(userId, SecurityVersionService.ChangeReason.ROLE_CHANGED);
            }
        }
        return authRepository.roleView(roleId);
    }

    private long requireRoleId(Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw AuthApiException.badRequest("角色ID不能为空");
        }
        return roleId;
    }

    @Transactional
    public AuthUserView updateUserStatus(CurrentUser currentUser, long userId, String accountStatus) {
        requireAdmin(currentUser);
        if (!authRepository.userExists(userId)) {
            throw AuthApiException.notFound("用户不存在");
        }
        AccountStatus status = parseAccountStatus(accountStatus);
        authRepository.updateAccountStatus(userId, status);
        authRepository.revokeUserSessions(userId, LocalDateTime.now());
        securityVersionService.advance(userId, SecurityVersionService.ChangeReason.ACCOUNT_STATUS_CHANGED);
        authAuditService.record(currentUser.id(), "ACCOUNT_STATUS_UPDATED", "AUTH_USER", String.valueOf(userId), "SUCCESS", status.name());
        return authRepository.toUserView(userId);
    }

    @Transactional
    public AuthUserView updateUserRoles(CurrentUser currentUser, long userId, List<Long> roleIds) {
        requireAdmin(currentUser);
        if (!authRepository.userExists(userId)) {
            throw AuthApiException.notFound("用户不存在");
        }
        if (!authRepository.enabledRolesExist(roleIds)) {
            throw AuthApiException.conflict("角色状态不可用或不存在");
        }
        authRepository.replaceUserRoles(userId, roleIds, currentUser.id());
        securityVersionService.advance(userId, SecurityVersionService.ChangeReason.ROLE_CHANGED);
        authAuditService.record(currentUser.id(), "USER_ROLE_UPDATED", "AUTH_USER", String.valueOf(userId), "SUCCESS", null);
        return authRepository.toUserView(userId);
    }

    @Transactional
    public RoleView updateRolePermissions(CurrentUser currentUser, long roleId, List<Long> permissionIds) {
        requireAdmin(currentUser);
        if (!authRepository.roleExists(roleId)) {
            throw AuthApiException.notFound("角色不存在");
        }
        List<Long> normalizedPermissionIds = permissionIds == null ? List.of() : permissionIds;
        if (!authRepository.enabledPermissionsExist(normalizedPermissionIds)) {
            throw AuthApiException.conflict("权限状态不可用或不存在");
        }
        authRepository.replaceRolePermissions(roleId, normalizedPermissionIds, currentUser.id());
        for (Long userId : authRepository.userIdsForRole(roleId)) {
            securityVersionService.advance(userId, SecurityVersionService.ChangeReason.PERMISSION_CHANGED);
        }
        authAuditService.record(currentUser.id(), "ROLE_PERMISSION_UPDATED", "AUTH_ROLE", String.valueOf(roleId), "SUCCESS", null);
        return authRepository.roleView(roleId);
    }

    private void requireAdmin(CurrentUser currentUser) {
        if (!currentUser.hasRole("ADMIN")) {
            authAuditService.record(
                    currentUser.id(),
                    "ADMIN_ACCESS_DENIED",
                    "ADMIN_API",
                    null,
                    "FAILURE",
                    "无管理员权限"
            );
            throw new AccessDeniedException("无管理员权限");
        }
    }

    private AccountStatus parseAccountStatus(String value) {
        try {
            return AccountStatus.valueOf(requireText(value, "账号状态不能为空").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw AuthApiException.badRequest("账号状态不合法");
        }
    }

    private LocalDateTime parseTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw AuthApiException.badRequest(fieldName + "格式不合法");
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
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
