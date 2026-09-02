package com.onlinejudge.gradeservice.security;

import com.onlinejudge.common.security.CurrentUser;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;

@Component
public class GradeCurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    private final GradeJwksCache jwks;

    public GradeCurrentUserArgumentResolver(GradeJwksCache jwks) { this.jwks = jwks; }

    @Override public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        try {
            GradeJwtVerifier.Claims claims = jwks.verify(request.getHeader("Authorization"));
            long userId = Long.parseLong(claims.string("userId"));
            String username = claims.values().get("username") instanceof String value ? value : "";
            var roles = new LinkedHashSet<>(claims.strings("roles"));
            var permissions = new LinkedHashSet<>(claims.strings("permissions"));
            String primaryRole = roles.stream().findFirst().orElse("");
            return new CurrentUser(userId, username, primaryRole, roles, permissions);
        } catch (Exception invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "valid bearer token is required");
        }
    }
}
