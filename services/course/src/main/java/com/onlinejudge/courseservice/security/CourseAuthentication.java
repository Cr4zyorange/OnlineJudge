package com.onlinejudge.courseservice.security;

import com.onlinejudge.courseservice.config.CourseIdentityProperties;
import com.onlinejudge.courseservice.persistence.CourseEventInboxRepository;
import com.onlinejudge.courseservice.web.CourseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CourseAuthentication {
    private final JwksCache jwksCache;
    private final CourseIdentityProperties properties;
    private final CourseEventInboxRepository inbox;

    public CourseAuthentication(JwksCache jwksCache, CourseIdentityProperties properties, CourseEventInboxRepository inbox) {
        this.jwksCache = jwksCache;
        this.properties = properties;
        this.inbox = inbox;
    }

    public CurrentUser user(HttpServletRequest request) {
        try {
            JwtVerifier.Claims claims = jwksCache.verify(request.getHeader("Authorization"), properties.getAudience());
            long id = Long.parseLong(claims.string("userId"));
            long securityVersion = claims.number("securityVersion");
            if (!inbox.accepts(id, securityVersion)) {
                throw new CourseException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "valid bearer token is required", false);
            }
            return new CurrentUser(id, new LinkedHashSet<>(claims.strings("roles")),
                    new LinkedHashSet<>(claims.strings("permissions")), securityVersion);
        } catch (CourseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CourseException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "valid bearer token is required", false);
        }
    }

    public ServicePrincipal service(HttpServletRequest request, String requiredScope) {
        String header = request.getHeader("X-OnlineJudge-Service-Authorization");
        if (header != null && !header.isBlank()) {
            return serviceJwt(header, requiredScope);
        }
        return serviceMtls(request, requiredScope);
    }

    private ServicePrincipal serviceJwt(String header, String requiredScope) {
        try {
            JwtVerifier.Claims claims = jwksCache.verify(header, "course");
            var scopes = new LinkedHashSet<>(claims.strings("scopes"));
            if (!scopes.contains(requiredScope)) {
                throw new CourseException(HttpStatus.FORBIDDEN, "SERVICE_IDENTITY_FORBIDDEN", "service scope is insufficient", false);
            }
            return new ServicePrincipal(claims.string("sub"), scopes);
        } catch (CourseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CourseException(HttpStatus.UNAUTHORIZED, "SERVICE_IDENTITY_INVALID", "valid course service identity is required", false);
        }
    }

    /**
     * course.openapi.json declares {serviceJwt} OR {mTLS} for the internal
     * endpoints.  A workload that presents a trusted client certificate is
     * authorized as an equivalent service principal; the deployment maps the
     * certificate subject to the endpoint scope (same rule as Learning).
     */
    private ServicePrincipal serviceMtls(HttpServletRequest request, String requiredScope) {
        X509Certificate certificate = clientCertificate(request);
        if (certificate == null) {
            throw new CourseException(HttpStatus.UNAUTHORIZED, "SERVICE_IDENTITY_INVALID",
                    "valid course service identity is required", false);
        }
        String subject = certificate.getSubjectX500Principal().getName();
        Set<String> allowedSubjects = Arrays.stream(properties.getMtlsServiceSubjects().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (!allowedSubjects.contains(subject)) {
            throw new CourseException(HttpStatus.FORBIDDEN, "SERVICE_IDENTITY_FORBIDDEN",
                    "service scope is insufficient", false);
        }
        return new ServicePrincipal(subject, Set.of(requiredScope));
    }

    private X509Certificate clientCertificate(HttpServletRequest request) {
        Object value = request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (!(value instanceof X509Certificate[] certificates) || certificates.length == 0) {
            value = request.getAttribute("javax.servlet.request.X509Certificate");
        }
        return value instanceof X509Certificate[] certificates && certificates.length > 0 ? certificates[0] : null;
    }

    public record ServicePrincipal(String subject, java.util.Set<String> scopes) { }
}
