package com.onlinejudge.assessmentservice.config;

import com.onlinejudge.assessmentservice.security.AssessmentAuthentication;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AssessmentWebConfig implements WebMvcConfigurer {
    private final AssessmentAuthentication authentication;
    public AssessmentWebConfig(AssessmentAuthentication authentication) { this.authentication = authentication; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(new HandlerInterceptor() {
        @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            try { CurrentUser user = authentication.user(request); request.setAttribute("assessment.currentUser", user); return true; }
            catch (RuntimeException rejected) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "valid bearer token is required"); return false; }
        }
    }).addPathPatterns("/api/**"); }
}
