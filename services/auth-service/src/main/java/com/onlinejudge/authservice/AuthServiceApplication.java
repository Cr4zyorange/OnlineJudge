package com.onlinejudge.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.onlinejudge.authservice.config.AuthBuildProperties;

@SpringBootApplication(scanBasePackages = "com.onlinejudge")
@EnableConfigurationProperties(AuthBuildProperties.class)
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
