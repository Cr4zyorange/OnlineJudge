package com.onlinejudge.assessmentservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AssessmentAuthentication {
    private final JwksCache jwks;
    public AssessmentAuthentication(JwksCache jwks) { this.jwks = jwks; }
    public CurrentUser user(HttpServletRequest request) {
        JwtVerifier.Claims claims = jwks.verifyUser(request.getHeader("Authorization"));
        return new CurrentUser(claims.string("userId"), Set.copyOf(claims.strings("roles")), claims.number("securityVersion"));
    }
    private static final class Set { private Set() {} static <T> java.util.Set<T> copyOf(java.util.Collection<T> value) { return java.util.Set.copyOf(value); } }
}
