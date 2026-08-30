package com.onlinejudge.courseservice;

import com.onlinejudge.courseservice.config.CourseIdentityProperties;
import com.onlinejudge.courseservice.config.CourseRabbitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({CourseIdentityProperties.class, CourseRabbitProperties.class})
public class CourseServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseServiceApplication.class, args);
    }
}
