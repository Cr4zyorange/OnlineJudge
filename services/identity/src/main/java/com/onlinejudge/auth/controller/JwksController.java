package com.onlinejudge.auth.controller;

import com.onlinejudge.auth.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
@RestController
public class JwksController {
    private final JwtTokenService jwtTokenService;
    private final Duration cacheMaxAge;

    public JwksController(
            JwtTokenService jwtTokenService,
            @Value("${onlinejudge.identity.jwks.cache-max-age:PT5M}") Duration cacheMaxAge
    ) {
        this.jwtTokenService = jwtTokenService;
        if (cacheMaxAge.isZero() || cacheMaxAge.isNegative()) {
            throw new IllegalArgumentException("Identity JWKS cache max age must be positive");
        }
        this.cacheMaxAge = cacheMaxAge;
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<?> jwks(@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ServiceTokenError(
                    "REQUEST_ID_REQUIRED",
                    "X-Request-Id is required",
                    "unknown",
                    false
            ));
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(cacheMaxAge).cachePublic().mustRevalidate())
                .body(jwtTokenService.jwks());
    }
}
