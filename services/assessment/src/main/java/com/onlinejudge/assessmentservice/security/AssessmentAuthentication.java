package com.onlinejudge.assessmentservice.security;

import jakarta.servlet.http.HttpServletRequest;
import com.onlinejudge.assessmentservice.service.IdentitySecurityVersionProjectionService;
import org.springframework.stereotype.Component;

@Component
public class AssessmentAuthentication {
    private final JwksCache jwks; private final IdentitySecurityVersionProjectionService securityVersions;
    public AssessmentAuthentication(JwksCache jwks, IdentitySecurityVersionProjectionService securityVersions) { this.jwks = jwks; this.securityVersions = securityVersions; }
    public CurrentUser user(HttpServletRequest request) {
        JwtVerifier.Claims claims = jwks.verifyUser(request.getHeader("Authorization"));
        String userId = claims.string("userId");
        long securityVersion = claims.number("securityVersion");
        if (securityVersion < securityVersions.minimumFor(userId)) throw new JwtVerifier.Rejected("security version is revoked");
        return new CurrentUser(userId, Set.copyOf(claims.strings("roles")), securityVersion);
    }
    private static final class Set { private Set() {} static <T> java.util.Set<T> copyOf(java.util.Collection<T> value) { return java.util.Set.copyOf(value); } }
}
