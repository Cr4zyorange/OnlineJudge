package com.onlinejudge.courseservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.config.CourseRabbitProperties;
import com.onlinejudge.courseservice.persistence.CourseOutboxRepository;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.AMQP;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Broker failure only leaves durable PENDING facts for retry; it cannot roll a committed Course command back. */
@Component
public class CourseOutboxRelay {
    private final CourseOutboxRepository outbox;
    private final CourseRabbitProperties rabbit;
    private final ObjectMapper objectMapper;

    public CourseOutboxRelay(CourseOutboxRepository outbox, CourseRabbitProperties rabbit, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        if (!rabbit.isEnabled()) return;
        for (CourseOutboxRepository.OutboxRecord record : outbox.due(50)) {
            try {
                publish(record);
                outbox.published(record.id());
            } catch (Exception exception) {
                outbox.retry(record.id(), exception.getClass().getSimpleName());
            }
        }
    }

    private void publish(CourseOutboxRepository.OutboxRecord record) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbit.getHost()); factory.setPort(rabbit.getPort());
        factory.setUsername(rabbit.getUsername()); factory.setPassword(rabbit.getPassword());
        try (Connection connection = factory.newConnection("course-outbox-relay"); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(rabbit.getExchange(), "topic", true);
            channel.confirmSelect();
            AtomicBoolean returned = new AtomicBoolean(false);
            channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, body) -> returned.set(true));
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", record.eventId()); envelope.put("eventType", record.eventType());
            envelope.put("payloadVersion", 2); envelope.put("aggregateType", record.aggregateType());
            envelope.put("aggregateId", record.aggregateId()); envelope.put("aggregateVersion", record.aggregateVersion());
            envelope.put("occurredAt", java.time.Instant.now().toString()); envelope.put("correlationId", record.correlationId());
            envelope.put("payload", objectMapper.readValue(record.payloadJson(), new TypeReference<Map<String, Object>>() { }));
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2).contentType("application/json")
                    .headers(Map.of("eventId", record.eventId(), "correlationId", record.correlationId())).build();
            channel.basicPublish(rabbit.getExchange(), record.routingKey(), true, properties,
                    objectMapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8));
            if (!channel.waitForConfirms(5_000) || returned.get()) {
                throw new IllegalStateException("RabbitMQ did not confirm Course outbox publication");
            }
        }
    }
}
