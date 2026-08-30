package com.onlinejudge.common.reliability;

/**
 * A broker adapter must return only after the broker has confirmed the publish.
 * It has no authority to change the source business transaction.
 */
public interface ConfirmedEventPublisher {
    void publish(ReliableEventEnvelope envelope, String routingKey);
}
