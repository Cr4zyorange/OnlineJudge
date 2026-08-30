package com.onlinejudge.courseservice.security;

import com.onlinejudge.courseservice.config.CourseIdentityProperties;
import com.onlinejudge.courseservice.persistence.CourseEventInboxRepository;
import com.onlinejudge.courseservice.web.CourseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

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
        try {
            JwtVerifier.Claims claims = jwksCache.verify(request.getHeader("X-OnlineJudge-Service-Authorization"), "course");
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

    public record ServicePrincipal(String subject, java.util.Set<String> scopes) { }
}
