package com.onlinejudge.auth.config;

import com.onlinejudge.auth.controller.RegisterRequest;
import com.onlinejudge.auth.repository.AuthRepository;
import com.onlinejudge.auth.service.AuthService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthSeedDataInitializer {
    @Bean
    ApplicationRunner authSeedData(AuthRepository authRepository, AuthService authService) {
        return args -> {
            authRepository.ensureBaseRolesAndPermissions();
            seedUser(authRepository, authService, "student001", "Student001@pass", "STUDENT", "演示学生");
            seedUser(authRepository, authService, "teacher001", "Teacher001@pass", "TEACHER", "演示教师");
            seedUser(authRepository, authService, "admin001", "Admin001@pass", "ADMIN", "演示管理员");
        };
    }

    private void seedUser(
            AuthRepository authRepository,
            AuthService authService,
            String username,
            String password,
            String userType,
            String displayName
    ) {
        if (authRepository.findUserByUsername(username).isPresent()) {
            return;
        }
        authService.register(new RegisterRequest(
                username,
                password,
                userType,
                displayName,
                null,
                username + "@example.com",
                null
        ));
    }
}
