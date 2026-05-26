package com.onlinejudge.common.event;

public interface NotificationEventPublisher {
    void publish(NotificationEvent event);
}
