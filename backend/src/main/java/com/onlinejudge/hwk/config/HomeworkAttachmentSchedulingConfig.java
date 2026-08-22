package com.onlinejudge.hwk.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "onlinejudge.hwk.attachments",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HomeworkAttachmentSchedulingConfig {
}
