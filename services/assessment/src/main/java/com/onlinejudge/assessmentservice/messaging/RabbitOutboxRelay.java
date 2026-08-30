package com.onlinejudge.assessmentservice.messaging;

import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** At-least-once outbox relay: a DB record changes state only after Rabbit publisher confirm. */
@Component
@ConditionalOnProperty(name = {"assessment.rabbit.enabled", "assessment.rabbit.relay-enabled"}, havingValue = "true")
public class RabbitOutboxRelay {
    public static final int PERSISTENT_DELIVERY_MODE = 2;
    private final AssessmentOutboxRepository outbox;
    private final String host, username, password, exchange;
    private final int port;

    public RabbitOutboxRelay(AssessmentOutboxRepository outbox, @Value("${assessment.rabbit.host:127.0.0.1}") String host,
            @Value("${assessment.rabbit.port:5672}") int port, @Value("${assessment.rabbit.username:guest}") String username,
            @Value("${assessment.rabbit.password:guest}") String password, @Value("${assessment.rabbit.exchange:onlinejudge.events.v2}") String exchange) {
        this.outbox = outbox; this.host = host; this.port = port; this.username = username; this.password = password; this.exchange = exchange;
    }

    @Scheduled(fixedDelayString = "${assessment.rabbit.relay-interval:1000}")
    public void publishPending() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host); factory.setPort(port); factory.setUsername(username); factory.setPassword(password);
        // A broker outage is expected.  Return promptly so the readiness
        // scheduler can withdraw the worker marker instead of reporting a
        // stale consumer as healthy for the client's one-minute default.
        factory.setConnectionTimeout(1_000); factory.setHandshakeTimeout(1_000);
        try (Connection connection = factory.newConnection("assessment-outbox-relay"); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, "topic", true);
            for (var event : outbox.pending(100)) {
                try {
                    var properties = new AMQP.BasicProperties.Builder().deliveryMode(PERSISTENT_DELIVERY_MODE)
                            .contentType("application/json").messageId(event.eventId()).type(event.eventType()).build();
                    MandatoryRabbitPublisher.publish(channel, exchange, "onlinejudge." + event.eventType(), properties,
                            event.payloadJson().getBytes(StandardCharsets.UTF_8), "outbox event " + event.eventId());
                    outbox.markDelivered(event.eventId());
                } catch (Exception deliveryFailure) {
                    outbox.recordDeliveryFailure(event.eventId(), deliveryFailure.getMessage());
                }
            }
        } catch (Exception ignored) {
            // Leave PENDING; a later relay invocation safely retries the same event id.
        }
    }
}
