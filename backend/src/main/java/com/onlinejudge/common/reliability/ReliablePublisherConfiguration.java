package com.onlinejudge.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReliablePublisherConfiguration {
    @Bean
    @ConditionalOnProperty(name = "onlinejudge.reliability.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
    ConfirmedEventPublisher unavailableConfirmedEventPublisher() {
        return new UnavailableConfirmedEventPublisher();
    }
}
