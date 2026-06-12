package com.onlinejudge.common.controller;

import com.onlinejudge.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SystemHealthController {
    @GetMapping("/api/v1/system/health")
    public ApiResponse<Map<String, String>> health() {
        // Keep the payload minimal so it is safe for public liveness checks.
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
