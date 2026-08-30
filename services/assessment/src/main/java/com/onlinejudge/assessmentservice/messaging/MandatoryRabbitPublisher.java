package com.onlinejudge.assessmentservice.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** A publisher confirm alone is insufficient: mandatory messages can be returned as unroutable. */
final class MandatoryRabbitPublisher {
    private MandatoryRabbitPublisher() {
    }

    static void publish(Channel channel, String exchange, String routingKey, AMQP.BasicProperties properties,
                        byte[] body, String operation) throws IOException, TimeoutException, InterruptedException {
        AtomicReference<String> returned = new AtomicReference<>();
        channel.confirmSelect();
        channel.addReturnListener(returnedMessage -> returned.compareAndSet(
                null, returnedMessage.getReplyCode() + " " + returnedMessage.getReplyText()));
        try {
            channel.basicPublish(exchange, routingKey, true, properties, body);
            if (!channel.waitForConfirms(5_000)) {
                throw new IllegalStateException(operation + " was not broker-confirmed");
            }
            String returnReason = returned.get();
            if (returnReason != null) {
                throw new IllegalStateException(operation + " was unroutable: " + returnReason);
            }
        } finally {
            channel.clearReturnListeners();
        }
    }
}
