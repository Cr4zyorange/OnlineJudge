package com.onlinejudge.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "onlinejudge.reliability.rabbitmq.enabled", havingValue = "true")
public class RabbitMqConfirmedEventPublisher implements ConfirmedEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitMqConfirmedEventPublisher(
            @Qualifier("reliableRabbitTemplate") RabbitTemplate reliableRabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = reliableRabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ReliableEventEnvelope envelope, String routingKey) {
        try {
            CorrelationData correlation = new CorrelationData(envelope.eventId());
            rabbitTemplate.convertAndSend(
                    RabbitMqReliabilityConfiguration.EVENTS_EXCHANGE,
                    routingKey,
                    transportEnvelope(envelope),
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

    /**
     * Jackson's Java-time default serializes an {@link java.time.Instant} as
     * a numeric epoch. Learning's closed v2 envelope deliberately requires
     * RFC3339 text, so transport must not expose the JVM's serialization
     * preference as a cross-service contract.
     */
    private Map<String, Object> transportEnvelope(ReliableEventEnvelope envelope) {
        Map<String, Object> transport = new LinkedHashMap<>();
        transport.put("eventId", envelope.eventId());
        transport.put("eventType", envelope.eventType());
        transport.put("payloadVersion", envelope.payloadVersion());
        transport.put("aggregateType", envelope.aggregateType());
        transport.put("aggregateId", envelope.aggregateId());
        transport.put("aggregateVersion", envelope.aggregateVersion());
        transport.put("occurredAt", envelope.occurredAt().toString());
        transport.put("correlationId", envelope.correlationId());
        transport.put("payload", objectMapper.convertValue(envelope.payload(), Object.class));
        return transport;
    }
}
