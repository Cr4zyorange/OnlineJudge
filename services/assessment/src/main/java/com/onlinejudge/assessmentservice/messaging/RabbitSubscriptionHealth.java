package com.onlinejudge.assessmentservice.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

/**
 * Readiness state for one durable consumer channel.  Rabbit Java's automatic
 * recovery recreates the channel and its consumers asynchronously, so a
 * successful TCP probe alone is not sufficient to call the worker ready.
 */
final class RabbitSubscriptionHealth {
    private volatile boolean connectionAvailable;
    private volatile boolean primarySubscribed;
    private volatile boolean deadLetterSubscribed;

    void subscriptionsEstablished() {
        connectionAvailable = true;
        primarySubscribed = true;
        deadLetterSubscribed = true;
    }

    void unavailable() {
        connectionAvailable = false;
        primarySubscribed = false;
        deadLetterSubscribed = false;
    }

    void recovered() {
        // Recovery callbacks run only after the client's recorded topology,
        // including both basicConsume registrations, has been restored.
        subscriptionsEstablished();
    }

    void primaryConsumerCancelled() {
        primarySubscribed = false;
    }

    void deadLetterConsumerCancelled() {
        deadLetterSubscribed = false;
    }

    boolean isHealthy(Connection connection, Channel channel) {
        return connectionAvailable && primarySubscribed && deadLetterSubscribed
                && connection != null && connection.isOpen()
                && channel != null && channel.isOpen();
    }
}
