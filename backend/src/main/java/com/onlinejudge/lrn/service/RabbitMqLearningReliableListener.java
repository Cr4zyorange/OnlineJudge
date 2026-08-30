package com.onlinejudge.lrn.service;

import com.onlinejudge.common.reliability.EventProcessingDecision;
import com.onlinejudge.common.reliability.RabbitMqReliabilityConfiguration;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Rabbit delivery boundary: the application owns manual ACK decisions. */
@Component
@ConditionalOnProperty(name = "onlinejudge.reliability.rabbitmq.enabled", havingValue = "true")
public class RabbitMqLearningReliableListener {
    private final LearningReliableEventConsumer consumer;

    public RabbitMqLearningReliableListener(LearningReliableEventConsumer consumer) {
        this.consumer = consumer;
    }

    @RabbitListener(queues = RabbitMqReliabilityConfiguration.LEARNING_QUEUE, ackMode = "MANUAL")
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        EventProcessingDecision decision;
        try {
            decision = consumer.consume(consumer.deserialize(new String(message.getBody(), StandardCharsets.UTF_8)));
        } catch (RuntimeException malformedMessage) {
            // A malformed envelope cannot provide a trustworthy eventId for the
            // application audit table, but the durable broker DLQ still retains it.
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        if (decision == EventProcessingDecision.ACK) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        if (decision == EventProcessingDecision.RETRY) {
            com.rabbitmq.client.AMQP.BasicProperties.Builder retryProperties =
                    new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                            .deliveryMode(2)
                            .headers(message.getMessageProperties().getHeaders());
            if (message.getMessageProperties().getContentType() != null) {
                retryProperties.contentType(message.getMessageProperties().getContentType());
            }
            channel.basicPublish("", RabbitMqReliabilityConfiguration.LEARNING_RETRY_QUEUE,
                    retryProperties.build(),
                    message.getBody());
            channel.basicAck(deliveryTag, false);
            return;
        }
        channel.basicNack(deliveryTag, false, false);
    }
}
