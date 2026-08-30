package com.onlinejudge.identityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.onlinejudge.authservice.config.AuthBuildProperties;

@SpringBootApplication(scanBasePackages = "com.onlinejudge")
@EnableConfigurationProperties(AuthBuildProperties.class)
public class IdentityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
