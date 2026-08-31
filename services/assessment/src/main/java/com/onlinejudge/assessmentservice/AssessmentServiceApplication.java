package com.onlinejudge.assessmentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.onlinejudge.assessmentservice.security.AssessmentIdentityProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AssessmentIdentityProperties.class)
public class AssessmentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssessmentServiceApplication.class, args);
    }
}
