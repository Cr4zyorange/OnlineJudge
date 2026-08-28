package com.onlinejudge.authservice.controller;

import com.onlinejudge.authservice.config.AuthBuildProperties;
import com.onlinejudge.common.web.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class AuthSystemController {
    private final JdbcTemplate jdbcTemplate;
    private final AuthBuildProperties buildProperties;

    public AuthSystemController(JdbcTemplate jdbcTemplate, AuthBuildProperties buildProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<Map<String, String>>> readiness() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "UP")));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("503", "service unavailable"));
        }
    }

    @GetMapping("/version")
    public ApiResponse<Map<String, String>> version() {
        return ApiResponse.ok(Map.of(
                "service", "auth-service",
                "version", buildProperties.version(),
                "revision", buildProperties.revision()
        ));
    }
}
