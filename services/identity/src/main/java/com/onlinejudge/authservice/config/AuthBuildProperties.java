package com.onlinejudge.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "onlinejudge.build")
public record AuthBuildProperties(String version, String revision) {
    public AuthBuildProperties {
        version = valueOrUnknown(version);
        revision = valueOrUnknown(revision);
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
