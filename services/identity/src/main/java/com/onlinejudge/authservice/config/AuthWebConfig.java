package com.onlinejudge.authservice.config;

import com.onlinejudge.common.security.AuthRequiredInterceptor;
import com.onlinejudge.common.security.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final CurrentUserArgumentResolver resolver;
    private final AuthRequiredInterceptor interceptor;

    public AuthWebConfig(CurrentUserArgumentResolver resolver, AuthRequiredInterceptor interceptor) {
        this.resolver = resolver;
        this.interceptor = interceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/system/health",
                        "/api/v1/system/readiness",
                        "/api/v1/system/version"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
