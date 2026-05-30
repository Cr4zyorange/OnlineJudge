package com.onlinejudge.auth.controller;

import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.service.AuthService;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {
    private final AuthService authService;

    public UserProfileController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<AuthUserView> profile(CurrentUser currentUser) {
        return ApiResponse.ok(authService.currentUser(currentUser.id()));
    }

    @PutMapping
    public ApiResponse<AuthUserView> updateProfile(
            CurrentUser currentUser,
            @RequestBody ProfileUpdateRequest request
    ) {
        return ApiResponse.ok(authService.updateProfile(currentUser.id(), request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            CurrentUser currentUser,
            @RequestBody PasswordChangeRequest request
    ) {
        authService.changePassword(currentUser.id(), request);
        return ApiResponse.ok(null);
    }
}
