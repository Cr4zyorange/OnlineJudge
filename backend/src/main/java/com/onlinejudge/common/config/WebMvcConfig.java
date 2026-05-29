package com.onlinejudge.common.config;

import com.onlinejudge.common.security.AuthRequiredInterceptor;
import com.onlinejudge.common.security.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final AuthRequiredInterceptor authRequiredInterceptor;

    public WebMvcConfig(
            CurrentUserArgumentResolver currentUserArgumentResolver,
            AuthRequiredInterceptor authRequiredInterceptor
    ) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.authRequiredInterceptor = authRequiredInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authRequiredInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/register");
    }
}
