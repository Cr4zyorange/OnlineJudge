package com.onlinejudge.lrn.service;

import com.onlinejudge.common.reliability.EventProcessingDecision;
import com.onlinejudge.common.reliability.RabbitMqReliabilityConfiguration;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
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
    private static final long RETRY_PUBLISH_CONFIRM_TIMEOUT_MILLIS = 5_000L;

    private final LearningReliableEventConsumer consumer;

    public RabbitMqLearningReliableListener(LearningReliableEventConsumer consumer) {
        this.consumer = consumer;
    }

    @RabbitListener(queues = RabbitMqReliabilityConfiguration.LEARNING_QUEUE, ackMode = "MANUAL")
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        EventProcessingDecision decision;
        try {
            ReliableEventEnvelope envelope = consumer.deserialize(new String(message.getBody(), StandardCharsets.UTF_8));
            decision = consumer.consume(envelope);
            if (decision == EventProcessingDecision.RETRY) {
                publishRetryThenAcknowledgeOriginal(message, channel, deliveryTag, retryQueue(envelope));
                return;
            }
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
        channel.basicNack(deliveryTag, false, false);
    }

    private void publishRetryThenAcknowledgeOriginal(
            Message message, Channel channel, long deliveryTag, String retryQueue
    ) throws IOException {
        try {
            // The retry copy is itself a durable delivery.  Never acknowledge
            // the original until this channel has received a publisher confirm.
            channel.confirmSelect();
            com.rabbitmq.client.AMQP.BasicProperties.Builder retryProperties =
                    new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                            .deliveryMode(2)
                            .headers(message.getMessageProperties().getHeaders());
            if (message.getMessageProperties().getContentType() != null) {
                retryProperties.contentType(message.getMessageProperties().getContentType());
            }
            channel.basicPublish("", retryQueue,
                    retryProperties.build(),
                    message.getBody());
            channel.waitForConfirmsOrDie(RETRY_PUBLISH_CONFIRM_TIMEOUT_MILLIS);
            channel.basicAck(deliveryTag, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            requeueOriginal(channel, deliveryTag, exception);
        } catch (Exception exception) {
            requeueOriginal(channel, deliveryTag, exception);
        }
    }

    private String retryQueue(ReliableEventEnvelope envelope) {
        if ("course.member.changed.v2".equals(envelope.eventType())) {
            return RabbitMqReliabilityConfiguration.LEARNING_COURSE_MEMBER_RETRY_QUEUE;
        }
        return RabbitMqReliabilityConfiguration.LEARNING_RETRY_QUEUE;
    }

    private void requeueOriginal(Channel channel, long deliveryTag, Exception retryFailure) throws IOException {
        try {
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException nackFailure) {
            nackFailure.addSuppressed(retryFailure);
            throw nackFailure;
        }
    }
}
