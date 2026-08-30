package com.onlinejudge.courseservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("course.identity")
public class CourseIdentityProperties {
    private String issuer = "onlinejudge.identity.v2";
    private String audience = "onlinejudge.api";
    private String jwksUri = "";
    private String jwksTrustBundle = "";
    private Duration requestTimeout = Duration.ofSeconds(1);
    private boolean refreshEnabled = true;
    private Duration refreshInitialDelay = Duration.ofSeconds(30);
    private Duration refreshInterval = Duration.ofMinutes(5);

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }
    public String getJwksTrustBundle() { return jwksTrustBundle; }
    public void setJwksTrustBundle(String jwksTrustBundle) { this.jwksTrustBundle = jwksTrustBundle; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public boolean isRefreshEnabled() { return refreshEnabled; }
    public void setRefreshEnabled(boolean refreshEnabled) { this.refreshEnabled = refreshEnabled; }
    public Duration getRefreshInitialDelay() { return refreshInitialDelay; }
    public void setRefreshInitialDelay(Duration refreshInitialDelay) { this.refreshInitialDelay = refreshInitialDelay; }
    public Duration getRefreshInterval() { return refreshInterval; }
    public void setRefreshInterval(Duration refreshInterval) { this.refreshInterval = refreshInterval; }
}
