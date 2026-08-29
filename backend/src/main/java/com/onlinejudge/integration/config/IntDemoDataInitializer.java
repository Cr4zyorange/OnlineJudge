package com.onlinejudge.integration.config;

import com.onlinejudge.integration.learning.LearningUserClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.OptionalLong;

@Configuration
@ConditionalOnProperty(name = "onlinejudge.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class IntDemoDataInitializer {
    @Bean
    @DependsOn("authSeedData")
    @Order(1)
    ApplicationRunner intDemoData(LearningUserClient users, List<DemoDataSeeder> seeders) {
        return new DemoDataRunner(users, seeders);
    }

    private record DemoDataRunner(LearningUserClient users, List<DemoDataSeeder> seeders) implements ApplicationRunner {
        @Override
        public void run(ApplicationArguments args) {
            OptionalLong teacher = users.findUserIdByUsername("teacher001");
            OptionalLong student = users.findUserIdByUsername("student001");
            if (teacher.isEmpty() || student.isEmpty()) return;
            DemoDataContext context = DemoDataContext.current(teacher.getAsLong(), student.getAsLong());
            seeders.forEach(seeder -> seeder.seed(context));
        }
    }
}
