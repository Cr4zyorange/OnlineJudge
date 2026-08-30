package com.onlinejudge.auth.controller;

import com.onlinejudge.auth.exception.ServiceTokenException;
import com.onlinejudge.auth.security.JwtTokenService;
import com.onlinejudge.auth.service.ServiceTokenService;
import com.onlinejudge.auth.service.WorkloadIdentityAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** mTLS-only implementation of contracts/v2/openapi/identity.openapi.json service-token endpoint. */
@RestController
@RequestMapping("/internal/v2")
public class ServiceTokenController {
    private final WorkloadIdentityAuthenticator workloadIdentityAuthenticator;
    private final ServiceTokenService serviceTokenService;

    public ServiceTokenController(
            WorkloadIdentityAuthenticator workloadIdentityAuthenticator,
            ServiceTokenService serviceTokenService
    ) {
        this.workloadIdentityAuthenticator = workloadIdentityAuthenticator;
        this.serviceTokenService = serviceTokenService;
    }

    @PostMapping("/service-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceTokenResponse mint(
            HttpServletRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ServiceTokenRequest body
    ) {
        if (requestId == null || requestId.isBlank()) {
            throw ServiceTokenException.badRequest("X-Request-Id is required");
        }
        if (body == null) {
            throw ServiceTokenException.badRequest("request body is required");
        }
        List<String> scopes = body.scopes() == null ? List.of() : body.scopes();
        String subject = workloadIdentityAuthenticator.authorize(request, body.audience(), scopes);
        JwtTokenService.IssuedServiceToken issued = serviceTokenService.mint(subject, body.audience(), scopes, idempotencyKey);
        return new ServiceTokenResponse(issued.token(), issued.expiresAt(), body.audience());
    }
}
