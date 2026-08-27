package com.onlinejudge.common.controller;

import com.onlinejudge.common.web.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SystemHealthController {
    private final JdbcTemplate jdbcTemplate;

    public SystemHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/v1/system/health")
    public ApiResponse<Map<String, String>> health() {
        // Keep the payload minimal so it is safe for public liveness checks.
        return ApiResponse.ok(Map.of("status", "UP"));
    }

    // LOCAL VERIFICATION STUB for issue #288 (never pushed/merged): mirrors the
    // readiness endpoint that D3 contract #293 assigns to issue #289.
    @GetMapping("/api/v1/system/readiness")
    public ResponseEntity<ApiResponse<Map<String, String>>> readiness() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (DataAccessException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("ERR-SYS-01", "database not ready"));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "UP")));
    }
}
