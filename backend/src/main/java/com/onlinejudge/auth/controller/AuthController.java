package com.onlinejudge.auth.controller;

import com.onlinejudge.auth.domain.AuthLoginResult;
import com.onlinejudge.auth.domain.AuthUserView;
import com.onlinejudge.auth.service.AuthService;
import com.onlinejudge.common.security.AuthenticationRequiredException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthUserView> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthLoginResult> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, CurrentUser currentUser) {
        currentUser.id();
        String token = extractToken(request);
        if (token == null) {
            throw new AuthenticationRequiredException("未登录或登录状态已失效");
        }
        authService.logout(currentUser.id(), token);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserView> me(CurrentUser currentUser) {
        return ApiResponse.ok(authService.currentUser(currentUser.id()));
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
