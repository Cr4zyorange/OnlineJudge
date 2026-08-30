package com.onlinejudge.common.reliability;

/** Keeps the API path available while RabbitMQ is offline or not configured. */
public class UnavailableConfirmedEventPublisher implements ConfirmedEventPublisher {
    @Override
    public void publish(ReliableEventEnvelope envelope, String routingKey) {
        throw new BrokerUnavailableException("RabbitMQ confirmed publisher is not available");
    }
}
