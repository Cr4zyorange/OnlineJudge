package com.onlinejudge.gradeservice.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "grade.rabbit.enabled", havingValue = "true")
public class RabbitGradeOutboxTransport implements GradeOutboxTransport {
    private final String host, username, password, exchange;
    private final int port;

    public RabbitGradeOutboxTransport(@Value("${grade.rabbit.host:127.0.0.1}") String host,
            @Value("${grade.rabbit.port:5672}") int port, @Value("${grade.rabbit.username:guest}") String username,
            @Value("${grade.rabbit.password:guest}") String password,
            @Value("${grade.rabbit.exchange:onlinejudge.events.v2}") String exchange) {
        this.host = host; this.port = port; this.username = username; this.password = password; this.exchange = exchange;
    }

    @Override
    public void publish(GradeOutboxRepository.OutboxEvent event) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host); factory.setPort(port); factory.setUsername(username); factory.setPassword(password);
        factory.setConnectionTimeout(1_000); factory.setHandshakeTimeout(1_000);
        try (Connection connection = factory.newConnection("grade-outbox-relay"); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, "topic", true);
            channel.confirmSelect();
            AtomicBoolean returned = new AtomicBoolean(false);
            channel.addReturnListener(returnedMessage -> returned.set(true));
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(2)
                    .contentType("application/json").messageId(event.eventId()).type(event.eventType())
                    .correlationId(event.correlationId()).build();
            channel.basicPublish(exchange, "onlinejudge." + event.eventType(), true, properties,
                    event.payloadJson().getBytes(StandardCharsets.UTF_8));
            if (!channel.waitForConfirms(5_000) || returned.get()) {
                throw new IllegalStateException("Rabbit did not confirm routable delivery for " + event.eventId());
            }
        }
    }
}
