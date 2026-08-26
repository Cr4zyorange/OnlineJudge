package com.onlinejudge.common.event;

public interface NotificationEventPublisher {
    /**
     * Publishes a best-effort notification event. Implementations may contain delivery failures.
     */
    void publish(NotificationEvent event);

    /**
     * Publishes a notification required by the source transaction. Implementations that contain
     * failures in {@link #publish(NotificationEvent)} must override this method and propagate them.
     */
    default void publishRequired(NotificationEvent event) {
        publish(event);
    }
}
