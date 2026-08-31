package com.onlinejudge.courseservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bounded Course -> LRN recent-task summary client settings.  The timeout is a
 * single bounded budget; an unconfigured base URL or token is a fail-closed
 * state, never a silent empty task list.
 */
@ConfigurationProperties("course.learning")
public class CourseLearningProperties {
    private String baseUrl = "";
    private Duration timeout = Duration.ofMillis(800);
    private String serviceToken = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}
