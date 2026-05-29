package com.onlinejudge.auth.controller;

import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.domain.PageResult;
import com.onlinejudge.auth.domain.PermissionView;
import com.onlinejudge.auth.domain.RoleView;
import com.onlinejudge.auth.service.RoleService;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AuthAdminController {
    private final RoleService roleService;

    public AuthAdminController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<AuthUserView>> listUsers(
            CurrentUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(roleService.listUsers(currentUser, keyword, role, status, page, size));
    }

    @PostMapping("/users")
    public ApiResponse<AuthUserView> createUser(CurrentUser currentUser, @RequestBody AdminCreateUserRequest request) {
        return ApiResponse.ok(roleService.createAdminUser(currentUser, request));
    }

    @PutMapping("/users/{userId}/status")
    public ApiResponse<AuthUserView> updateUserStatus(
            CurrentUser currentUser,
            @PathVariable long userId,
            @RequestBody AccountStatusRequest request
    ) {
        return ApiResponse.ok(roleService.updateUserStatus(currentUser, userId, request.accountStatus()));
    }

    @PutMapping("/users/{userId}/roles")
    public ApiResponse<AuthUserView> updateUserRoles(
            CurrentUser currentUser,
            @PathVariable long userId,
            @RequestBody RoleIdsRequest request
    ) {
        return ApiResponse.ok(roleService.updateUserRoles(currentUser, userId, request.roleIds()));
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleView>> listRoles(CurrentUser currentUser) {
        return ApiResponse.ok(roleService.listRoles(currentUser));
    }

    @PostMapping("/roles")
    public ApiResponse<RoleView> createRole(CurrentUser currentUser, @RequestBody RoleUpsertRequest request) {
        return ApiResponse.ok(roleService.createRole(currentUser, request));
    }

    @PutMapping("/roles")
    public ApiResponse<RoleView> updateRole(CurrentUser currentUser, @RequestBody RoleUpsertRequest request) {
        return ApiResponse.ok(roleService.updateRole(currentUser, request));
    }

    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<RoleView> updateRolePermissions(
            CurrentUser currentUser,
            @PathVariable long roleId,
            @RequestBody PermissionIdsRequest request
    ) {
        return ApiResponse.ok(roleService.updateRolePermissions(currentUser, roleId, request.permissionIds()));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionView>> listPermissions(CurrentUser currentUser) {
        return ApiResponse.ok(roleService.listPermissions(currentUser));
    }
}
