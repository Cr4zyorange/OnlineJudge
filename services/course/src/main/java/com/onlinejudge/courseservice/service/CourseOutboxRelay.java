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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Broker failure only leaves durable PENDING facts for retry; it cannot roll a committed Course command back. */
@Component
public class CourseOutboxRelay {
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 8;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration RETRY_BASE = Duration.ofSeconds(5);
    private static final Duration RETRY_MAXIMUM = Duration.ofMinutes(5);
    private final CourseOutboxRepository outbox;
    private final CourseRabbitProperties rabbit;
    private final ObjectMapper objectMapper;
    private final String leaseOwner = "course-outbox-relay-" + UUID.randomUUID();

    public CourseOutboxRelay(CourseOutboxRepository outbox, CourseRabbitProperties rabbit, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        relayOnce();
    }

    /** Visible for disposable two-relay acceptance: each invocation owns only the leases it won. */
    public int relayOnce() {
        if (!rabbit.isEnabled()) return 0;
        Instant now = Instant.now();
        var claimed = outbox.claimDue(leaseOwner, now, LEASE_DURATION, BATCH_SIZE);
        for (CourseOutboxRepository.OutboxRecord record : claimed) {
            try {
                publish(record);
                outbox.markPublished(record, Instant.now());
            } catch (Exception exception) {
                outbox.markFailedAttempt(record, Instant.now(), exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                        MAX_ATTEMPTS, RETRY_BASE, RETRY_MAXIMUM);
            }
        }
        return claimed.size();
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
