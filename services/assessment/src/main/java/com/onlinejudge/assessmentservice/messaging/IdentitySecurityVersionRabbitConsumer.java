package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.assessmentservice.service.IdentitySecurityVersionProjectionService;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Map;

/** At-least-once Identity consumer: it ACKs only after the Assessment-local inbox/minimum/gap transaction commits. */
@Component
@ConditionalOnProperty(name = "assessment.rabbit.enabled", havingValue = "true")
public class IdentitySecurityVersionRabbitConsumer implements SmartLifecycle {
    private final IdentitySecurityVersionProjectionService projection; private final ObjectMapper json;
    private final String host, username, password, exchange, queue, deadLetterExchange, deadLetterQueue, deadLetterRoutingKey; private final int port;
    private volatile boolean running; private Connection connection; private Channel channel;

    public IdentitySecurityVersionRabbitConsumer(IdentitySecurityVersionProjectionService projection, ObjectMapper json,
            @Value("${assessment.rabbit.host:127.0.0.1}") String host, @Value("${assessment.rabbit.port:5672}") int port,
            @Value("${assessment.rabbit.username:guest}") String username, @Value("${assessment.rabbit.password:guest}") String password,
            @Value("${assessment.rabbit.exchange:onlinejudge.events}") String exchange,
            @Value("${assessment.rabbit.identity-security-version-queue:assessment.identity-security-version.v2}") String queue,
            @Value("${assessment.rabbit.identity-security-version-dead-letter-exchange:onlinejudge.events.dlx.v2}") String deadLetterExchange,
            @Value("${assessment.rabbit.identity-security-version-dead-letter-queue:assessment.identity-security-version.dlq.v2}") String deadLetterQueue,
            @Value("${assessment.rabbit.identity-security-version-dead-letter-routing-key:onlinejudge.identity.security-version.changed.invalid.v2}") String deadLetterRoutingKey) {
        this.projection = projection; this.json = json; this.host = host; this.port = port; this.username = username; this.password = password;
        this.exchange = exchange; this.queue = queue; this.deadLetterExchange = deadLetterExchange; this.deadLetterQueue = deadLetterQueue; this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    @Override public void start() {
        try {
            var factory = new ConnectionFactory(); factory.setHost(host); factory.setPort(port); factory.setUsername(username); factory.setPassword(password);
            connection = factory.newConnection("assessment-identity-security-version-consumer"); channel = connection.createChannel();
            channel.exchangeDeclare(exchange, "topic", true); channel.exchangeDeclare(deadLetterExchange, "topic", true);
            channel.queueDeclare(deadLetterQueue, true, false, false, null); channel.queueBind(deadLetterQueue, deadLetterExchange, deadLetterRoutingKey);
            channel.queueDeclare(queue, true, false, false, Map.of("x-dead-letter-exchange", deadLetterExchange, "x-dead-letter-routing-key", deadLetterRoutingKey));
            channel.queueBind(queue, exchange, "onlinejudge.identity.security-version.changed.v2"); channel.basicQos(1);
            channel.basicConsume(queue, false, (tag, message) -> {
                try {
                    IdentitySecurityVersionEventEnvelope event = IdentitySecurityVersionEventEnvelope.parse(json.readTree(message.getBody()));
                    projection.apply(new IdentitySecurityVersionProjectionService.SecurityVersionChanged(event.eventId(), event.userId(), event.securityVersion(), event.changeReason(), event.aggregateVersion()));
                    channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
                } catch (IllegalArgumentException invalid) { channel.basicNack(message.getEnvelope().getDeliveryTag(), false, false); }
                catch (Exception unavailable) { channel.basicNack(message.getEnvelope().getDeliveryTag(), false, true); }
            }, tag -> { });
            running = true;
        } catch (Exception unavailable) { stop(); }
    }
    @Override public void stop() { running = false; try { if (channel != null) channel.close(); } catch (Exception ignored) { } try { if (connection != null) connection.close(); } catch (Exception ignored) { } }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return 1; }
}
