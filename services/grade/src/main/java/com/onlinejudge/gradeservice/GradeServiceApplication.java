package com.onlinejudge.gradeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.onlinejudge.gradeservice.security.GradeIdentityProperties;

@SpringBootApplication(scanBasePackages = {"com.onlinejudge.gradeservice", "com.onlinejudge.grd"})
@EnableScheduling
@EnableConfigurationProperties(GradeIdentityProperties.class)
public class GradeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GradeServiceApplication.class, args);
    }
}
