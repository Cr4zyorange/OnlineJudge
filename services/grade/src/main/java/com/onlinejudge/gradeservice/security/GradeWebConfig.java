package com.onlinejudge.gradeservice.security;

import org.springframework.stereotype.Component;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Component
public class GradeWebConfig implements WebMvcConfigurer {
    private final GradeCurrentUserArgumentResolver resolver;
    public GradeWebConfig(GradeCurrentUserArgumentResolver resolver) { this.resolver = resolver; }
    @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) { resolvers.add(resolver); }
}
