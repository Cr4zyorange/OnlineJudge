package com.onlinejudge.assessmentservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Set;

/** Internal rebuild endpoints accept an audience-bound service JWT or an explicitly configured mTLS principal. */
@Component
public class AssessmentServiceIdentityAuthentication {
    private final JwksCache jwks; private final AssessmentIdentityProperties identity; private final Set<String> mtlsGradeSubjects;
    public AssessmentServiceIdentityAuthentication(JwksCache jwks, AssessmentIdentityProperties identity,
            @Value("${assessment.internal.mtls-grade-subjects:}") String configuredSubjects) {
        this.jwks = jwks; this.identity = identity;
        this.mtlsGradeSubjects = Arrays.stream(configuredSubjects.split(",")).map(String::trim).filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public void requireGradesRead(HttpServletRequest request) {
        String header = request.getHeader("X-OnlineJudge-Service-Authorization");
        if (header != null && !header.isBlank()) {
            JwtVerifier.Claims claims;
            try { claims = jwks.verify(header, identity.getServiceAudience()); } catch (RuntimeException rejected) { throw ServiceIdentityException.invalid(); }
            try { if (!claims.strings("scopes").contains("grades:read")) throw ServiceIdentityException.forbidden(); }
            catch (JwtVerifier.Rejected malformed) { throw ServiceIdentityException.invalid(); }
            return;
        }
        X509Certificate certificate = clientCertificate(request);
        if (certificate == null) throw ServiceIdentityException.invalid();
        if (!mtlsGradeSubjects.contains(certificate.getSubjectX500Principal().getName())) throw ServiceIdentityException.forbidden();
    }
    private X509Certificate clientCertificate(HttpServletRequest request) {
        Object value = request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (!(value instanceof X509Certificate[] certificates) || certificates.length == 0) value = request.getAttribute("javax.servlet.request.X509Certificate");
        return value instanceof X509Certificate[] certificates && certificates.length > 0 ? certificates[0] : null;
    }
}
