package com.onlinejudge.assessmentservice.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("assessment.identity")
public class AssessmentIdentityProperties {
    private String issuer = "onlinejudge.identity.v2";
    private String audience = "onlinejudge.api";
    private String jwksUri = "";
    private String jwksTrustBundle = "";
    private Duration requestTimeout = Duration.ofSeconds(1);
    private boolean refreshEnabled = true;
    private Duration refreshInterval = Duration.ofMinutes(5);
    public String getIssuer() { return issuer; } public void setIssuer(String v) { issuer = v; }
    public String getAudience() { return audience; } public void setAudience(String v) { audience = v; }
    public String getJwksUri() { return jwksUri; } public void setJwksUri(String v) { jwksUri = v; }
    public String getJwksTrustBundle() { return jwksTrustBundle; } public void setJwksTrustBundle(String v) { jwksTrustBundle = v; }
    public Duration getRequestTimeout() { return requestTimeout; } public void setRequestTimeout(Duration v) { requestTimeout = v; }
    public boolean isRefreshEnabled() { return refreshEnabled; } public void setRefreshEnabled(boolean v) { refreshEnabled = v; }
    public Duration getRefreshInterval() { return refreshInterval; } public void setRefreshInterval(Duration v) { refreshInterval = v; }
}
