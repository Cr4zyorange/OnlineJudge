package com.onlinejudge.lab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class LabEvaluationAsyncConfig {
    @Bean(name = "labEvaluationExecutor")
    public Executor labEvaluationExecutor() {
        // A sync executor keeps test behavior deterministic while preserving the async boundary.
        return new SyncTaskExecutor();
    }
}
