package com.onlinejudge.courseservice.config;

import com.onlinejudge.courseservice.security.CourseAuthentication;
import com.onlinejudge.courseservice.web.CourseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

@Component
public class CourseWebConfig implements WebMvcConfigurer {
    private final CourseAuthentication authentication;

    public CourseWebConfig(CourseAuthentication authentication) { this.authentication = authentication; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestIdInterceptor()).addPathPatterns("/api/v1/**", "/internal/v2/**");
        registry.addInterceptor(new UserInterceptor(authentication)).addPathPatterns("/api/v1/courses/**");
        registry.addInterceptor(new InternalInterceptor(authentication)).addPathPatterns("/internal/v2/**");
    }

    private static class RequestIdInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String requestId = request.getHeader("X-Request-Id");
            if (requestId == null || requestId.isBlank()) {
                throw new CourseException(HttpStatus.BAD_REQUEST, "REQUEST_ID_REQUIRED", "X-Request-Id is required", false);
            }
            try { UUID.fromString(requestId); } catch (IllegalArgumentException invalid) {
                throw new CourseException(HttpStatus.BAD_REQUEST, "REQUEST_ID_INVALID", "X-Request-Id must be a UUID", false);
            }
            request.setAttribute("course.requestId", requestId);
            return true;
        }
    }

    private static class UserInterceptor implements HandlerInterceptor {
        private final CourseAuthentication authentication;
        UserInterceptor(CourseAuthentication authentication) { this.authentication = authentication; }
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            request.setAttribute("course.currentUser", authentication.user(request));
            return true;
        }
    }

    private static class InternalInterceptor implements HandlerInterceptor {
        private final CourseAuthentication authentication;
        InternalInterceptor(CourseAuthentication authentication) { this.authentication = authentication; }
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String requiredScope = request.getRequestURI().endsWith("/members")
                    ? "course.members.read" : "course.authorizations.read";
            request.setAttribute("course.servicePrincipal", authentication.service(request, requiredScope));
            return true;
        }
    }
}
