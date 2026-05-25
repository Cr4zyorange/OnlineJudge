package com.onlinejudge.common.event;

import org.springframework.stereotype.Component;

@Component
public class NoopNotificationEventPublisher implements NotificationEventPublisher {
    @Override
    public void publish(NotificationEvent event) {
        // LRN will replace this with persistent notification delivery.
    }
}
