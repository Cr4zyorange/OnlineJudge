package com.onlinejudge.lrn.security;

import com.onlinejudge.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Set;

/**
 * Internal Learning endpoints accept an audience-bound service JWT
 * (aud=learning, scope learning.tasks.read) or an explicitly configured mTLS
 * workload identity.  Missing/invalid service identity is 401
 * SERVICE_IDENTITY_INVALID; an authenticated principal without the scope is
 * 403 SERVICE_IDENTITY_FORBIDDEN, per the v2 OpenAPI contract.
 */
@Component
public class LearningServiceIdentityAuthentication {
    public static final String TASKS_READ_SCOPE = "learning.tasks.read";

    private final ServiceJwtVerifier verifier;
    private final Set<String> mtlsCourseSubjects;

    public LearningServiceIdentityAuthentication(
            ServiceJwtVerifier verifier,
            @Value("${onlinejudge.learning.internal.mtls-course-subjects:}") String configuredSubjects
    ) {
        this.verifier = verifier;
        this.mtlsCourseSubjects = Arrays.stream(configuredSubjects.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void requireTasksRead(HttpServletRequest request) {
        String header = request.getHeader("X-OnlineJudge-Service-Authorization");
        if (header != null && !header.isBlank()) {
            ServiceJwtVerifier.Claims claims;
            try {
                claims = verifier.verify(header);
            } catch (ServiceJwtVerifier.Rejected rejected) {
                throw new ApiException("SERVICE_IDENTITY_INVALID", "service identity is missing or invalid", HttpStatus.UNAUTHORIZED);
            }
            if (!claims.scopes().contains(TASKS_READ_SCOPE)) {
                throw new ApiException("SERVICE_IDENTITY_FORBIDDEN", "service scope is insufficient", HttpStatus.FORBIDDEN);
            }
            return;
        }
        X509Certificate certificate = clientCertificate(request);
        if (certificate == null) {
            throw new ApiException("SERVICE_IDENTITY_INVALID", "service identity is missing or invalid", HttpStatus.UNAUTHORIZED);
        }
        if (!mtlsCourseSubjects.contains(certificate.getSubjectX500Principal().getName())) {
            throw new ApiException("SERVICE_IDENTITY_FORBIDDEN", "service scope is insufficient", HttpStatus.FORBIDDEN);
        }
    }

    private X509Certificate clientCertificate(HttpServletRequest request) {
        Object value = request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (!(value instanceof X509Certificate[] certificates) || certificates.length == 0) {
            value = request.getAttribute("javax.servlet.request.X509Certificate");
        }
        return value instanceof X509Certificate[] certificates && certificates.length > 0 ? certificates[0] : null;
    }
}
