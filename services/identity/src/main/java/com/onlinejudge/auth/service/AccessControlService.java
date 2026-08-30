package com.onlinejudge.auth.service;

import com.onlinejudge.auth.controller.CheckPermissionRequest;
import com.onlinejudge.auth.domain.PermissionCheckResult;
import com.onlinejudge.auth.exception.AuthApiException;
import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.common.security.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {
    private final AuthAuditService authAuditService;

    public AccessControlService(AuthAuditService authAuditService) {
        this.authAuditService = authAuditService;
    }

    public PermissionCheckResult checkPermission(CurrentUser currentUser, CheckPermissionRequest request) {
        String permissionCode = requirePermissionCode(request.permissionCode());
        String resourceType = blankToNull(request.resourceType());
        String resourceId = blankToNull(request.resourceId());
        if (!currentUser.hasPermission(permissionCode)) {
            authAuditService.record(
                    currentUser.id(),
                    "ACCESS_DENIED",
                    resourceType == null ? "PERMISSION" : resourceType,
                    resourceId == null ? permissionCode : resourceId,
                    "DENIED",
                    "缺少权限：" + permissionCode
            );
            throw new AccessDeniedException("无权限访问");
        }
        return new PermissionCheckResult(true, permissionCode, resourceType, resourceId, null);
    }

    private String requirePermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw AuthApiException.badRequest("权限编码不能为空");
        }
        return permissionCode.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
