package com.onlinejudge.assessmentservice.config;

import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
class SubmissionStorageConfig {
    @Bean
    PersistentSubmissionFileStore submissionFileStore(@Value("${assessment.storage.root:./var/assessment-files}") String root) {
        return new PersistentSubmissionFileStore(Path.of(root));
    }
}
