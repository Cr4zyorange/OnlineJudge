package com.onlinejudge.common.reliability;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "onlinejudge.reliability.rabbitmq.enabled", havingValue = "true")
public class RabbitMqConfirmedEventPublisher implements ConfirmedEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public RabbitMqConfirmedEventPublisher(@Qualifier("reliableRabbitTemplate") RabbitTemplate reliableRabbitTemplate) {
        this.rabbitTemplate = reliableRabbitTemplate;
    }

    @Override
    public void publish(ReliableEventEnvelope envelope, String routingKey) {
        try {
            CorrelationData correlation = new CorrelationData(envelope.eventId());
            rabbitTemplate.convertAndSend(
                    RabbitMqReliabilityConfiguration.EVENTS_EXCHANGE,
                    routingKey,
                    envelope,
                    message -> {
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setHeader("eventId", envelope.eventId());
                        message.getMessageProperties().setHeader("correlationId", envelope.correlationId());
                        return message;
                    },
                    correlation
            );
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new BrokerUnavailableException("RabbitMQ rejected confirmed publish: "
                        + (confirm == null ? "no confirm" : confirm.getReason()));
            }
        } catch (BrokerUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BrokerUnavailableException("RabbitMQ confirmed publish failed", exception);
        }
    }
}
