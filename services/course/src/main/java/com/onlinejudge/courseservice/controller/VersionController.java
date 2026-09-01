package com.onlinejudge.courseservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VersionController {
    private final String version;
    private final String revision;

    public VersionController(@Value("${app.version:0.1.0-SNAPSHOT}") String version,
                             @Value("${app.revision:unknown}") String revision) {
        this.version = version;
        this.revision = revision;
    }

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of("service", "course-service", "version", version, "revision", revision);
    }
}
