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
    private boolean mtlsEnabled = false;
    private String mtlsKeystorePath = "";
    private String mtlsKeystorePassword = "";
    private String mtlsKeystoreType = "PKCS12";
    private String mtlsTruststorePath = "";
    private String mtlsTruststorePassword = "";
    private String mtlsTruststoreType = "PKCS12";

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

    public boolean isMtlsEnabled() { return mtlsEnabled; }
    public void setMtlsEnabled(boolean mtlsEnabled) { this.mtlsEnabled = mtlsEnabled; }
    public String getMtlsKeystorePath() { return mtlsKeystorePath; }
    public void setMtlsKeystorePath(String mtlsKeystorePath) { this.mtlsKeystorePath = mtlsKeystorePath; }
    public String getMtlsKeystorePassword() { return mtlsKeystorePassword; }
    public void setMtlsKeystorePassword(String mtlsKeystorePassword) { this.mtlsKeystorePassword = mtlsKeystorePassword; }
    public String getMtlsKeystoreType() { return mtlsKeystoreType; }
    public void setMtlsKeystoreType(String mtlsKeystoreType) { this.mtlsKeystoreType = mtlsKeystoreType; }
    public String getMtlsTruststorePath() { return mtlsTruststorePath; }
    public void setMtlsTruststorePath(String mtlsTruststorePath) { this.mtlsTruststorePath = mtlsTruststorePath; }
    public String getMtlsTruststorePassword() { return mtlsTruststorePassword; }
    public void setMtlsTruststorePassword(String mtlsTruststorePassword) { this.mtlsTruststorePassword = mtlsTruststorePassword; }
    public String getMtlsTruststoreType() { return mtlsTruststoreType; }
    public void setMtlsTruststoreType(String mtlsTruststoreType) { this.mtlsTruststoreType = mtlsTruststoreType; }
}
